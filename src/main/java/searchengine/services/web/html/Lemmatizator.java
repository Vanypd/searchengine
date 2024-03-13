package searchengine.services.web.html;

import lombok.NonNull;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.model.implementation.Index;
import searchengine.model.implementation.Lemma;
import searchengine.model.implementation.Page;
import searchengine.model.implementation.Site;
import searchengine.repository.RepositoryManager;
import searchengine.repository.implementation.IndexRepository;
import searchengine.repository.implementation.LemmaRepository;
import searchengine.repository.implementation.PageRepository;
import searchengine.repository.implementation.SiteRepository;
import searchengine.services.utils.URLParser;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

@Component
public class Lemmatizator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Lemmatizator.class);
    private static final String[] functionalPartsOfSpeech = new String[]{"СОЮЗ", "ПРЕДЛ", "МЕЖД", "ЧАСТ"};
    private final RepositoryManager repositoryManager;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;


    @Autowired
    public Lemmatizator(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
        siteRepository = repositoryManager.getSiteRepository();
        pageRepository = repositoryManager.getPageRepository();
        lemmaRepository = repositoryManager.getLemmaRepository();
        indexRepository = repositoryManager.getIndexRepository();
    }


    public void save(@NonNull String text, URL url) {
        String baseUrl = URLParser.getBaseUrl(url);
        String path = URLParser.getPathFromUrl(baseUrl, url);
        HashMap<String, Integer> lemmas = collectLemmas(text);

        Site site = siteRepository.findByUrl(baseUrl);
        Page page = pageRepository.findBySiteIdAndPath(site, path);

        repositoryManager.executeTransaction(() ->
            checkIndexExistence(page)
        );

        lemmas.forEach((str, count) -> {
            Lemma lemmaEntity = lemmaRepository.findByLemma(str);
            Index indexEntity;

            if (lemmaEntity == null) {
                lemmaEntity = createNewLemmaEntity(site, str);
                indexEntity = createNewIndexEntity(page, lemmaEntity, count);
            } else {
                indexEntity = indexRepository.findByPageIdAndLemmaId(page, lemmaEntity);
                lemmaEntity.setFrequency(lemmaEntity.getFrequency() + 1);
                indexEntity.setRank(indexEntity.getRank() + count);
            }

            saveLemmaAndIndex(lemmaEntity, indexEntity);
        });
    }


    private static HashMap<String, Integer> collectLemmas(String text) {

        if (text.isBlank()) {
            return new HashMap<>();
        }

        HashMap<String, Integer> result = new HashMap<>();
        LuceneMorphology luceneMorph = luceneMorphInitialization();
        String[] words = splitTextToWords(text);

        for (String word : words) {

            if (word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorph.getMorphInfo(word);
            String wordProps = wordBaseForms.get(0);

            if (isFunctionalPartOfSpeech(wordProps)) {
                continue;
            }

            List<String> normalForms = luceneMorph.getNormalForms(word);

            if (normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0);

            if (result.containsKey(normalWord)) {
                Integer count = result.get(normalWord);
                result.put(normalWord, count + 1);
                continue;
            }

            result.put(normalWord, 1);
        }

        return result;
    }


    // UTILS METHODS //


    /**
     * Метод проверяет является ли переданное в параметры слово служебной частью речи и возвращает
     * соответствующий boolean
     * @param wordWithProps Строка содержащая слово с его свойствами, например
     *                      "хитрый|Y КР_ПРИЛ ср,ед,од,но"
     * @return boolean
     */
    private static boolean isFunctionalPartOfSpeech(String wordWithProps) {
        for (String part : functionalPartsOfSpeech) {
            if (wordWithProps.contains(part)) {
              return true;
            }
        }
        return false;
    }


    /**
     * Метод принимает текст, переводит его в нижний регистр, а также удаляет все символы, которые не
     * являются символами русского алфавита или пробельным символом и заменяет все множественные пробельные
     * символы на один пробел. Затем идёт разделение текста на слова и возвращается массив строк.
     * @param text Текст, который необходимо разделить по словам
     * @return String[] - Массив строк
     */
    private static String[] splitTextToWords(String text) {
        return text.trim()
                .toLowerCase()
                .replaceAll("[^а-яё\\s]", "")
                .replaceAll("\\s+", " ")
                .split("\\s");
    }


    /**
     * Метод инициализирует новый LuceneMorphology и возвращает его.
     * @return LuceneMorphology
     */
    private static LuceneMorphology luceneMorphInitialization() {
        try {
            return new RussianLuceneMorphology();
        } catch (IOException e) {
            LOGGER.error("Не удалось инициализировать RussianLuceneMorphology", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Метод создаёт и возвращает новую сущность Lemma.
     * @param site Сущность сайта
     * @param str Строка с леммой
     * @return Lemma
     */
    private Lemma createNewLemmaEntity(Site site, String str) {
        Lemma lemmaEntity = new Lemma();
        lemmaEntity.setSiteId(site);
        lemmaEntity.setLemma(str);
        lemmaEntity.setFrequency(1L);
        return lemmaEntity;
    }


    /**
     * Метод создаёт и возвращает новую сущность Index.
     * @param page Сущность страницы
     * @param lemmaEntity Сущность леммы
     * @param count Количество повторений леммы на странице
     * @return Index
     */
    private Index createNewIndexEntity(Page page, Lemma lemmaEntity, Integer count) {
        Index indexEntity = new Index();
        indexEntity.setPageId(page);
        indexEntity.setLemmaId(lemmaEntity);
        indexEntity.setRank(count.floatValue());
        return indexEntity;
    }


    /**
     * Метод проверяет, существует ли индексы у переданной в параметры страницы, если индексы существуют
     * то из базы данных удаляются все индексы связанные с этой страницы, а так же обновляются/удаляются
     * связанные леммы.
     * @param page Сущность страницы
     */
    private void checkIndexExistence(Page page) {
        List<Index> indexList = indexRepository.findAllByPageId(page);

        if (!indexList.isEmpty()) {
            indexRepository.deleteAll(indexList);

            for (Index index : indexList) {
                Lemma lemma = index.getLemmaId();
                lemma.setFrequency(lemma.getFrequency() - 1);

                if (lemma.getFrequency() == 0) {
                    lemmaRepository.delete(lemma);
                }
            }
        }
    }


    /**
     *  Метод сохраняет Лемму и Индекс в базу данных в рамках одной транзакции.
     * @param lemma Сущность леммы которую необходимо сохранить
     * @param index Сущность индекса которую необходимо сохранить
     */
    private void saveLemmaAndIndex(Lemma lemma, Index index) {
        repositoryManager.executeTransaction(() -> {
            lemmaRepository.save(lemma);
            indexRepository.save(index);
        });
    }
}

