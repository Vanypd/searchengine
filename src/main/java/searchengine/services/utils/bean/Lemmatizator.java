package searchengine.services.utils.bean;

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
import searchengine.services.utils.notbean.HTMLManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

@Component
public final class Lemmatizator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Lemmatizator.class);
    private static final String[] FUNCTIONAL_PARTS_OF_SPEECH = new String[]{"СОЮЗ", "ПРЕДЛ", "МЕЖД", "ЧАСТ"};
    private final RepositoryManager repositoryManager;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;


    @Autowired
    public Lemmatizator(RepositoryManager repositoryManager) {
        this.repositoryManager = repositoryManager;
        lemmaRepository = repositoryManager.getLemmaRepository();
        indexRepository = repositoryManager.getIndexRepository();
    }


    /**
     * Метод принимает сущность страницы, а затем сохраняет все найденные на ней леммы в базу данных.
     * Все взаимодействия с базой проходят в транзакциях.
     * @param pageEntity Page
     */
    public void save(Page pageEntity) {
        Site siteEntity = pageEntity.getSiteId();
        String text = HTMLManager.getTextFromHTML(pageEntity.getContent());
        HashMap<String, Integer> lemmas = collectLemmas(text);

        repositoryManager.executeTransaction(() ->
                checkIndexExistence(pageEntity)
        );

        lemmas.forEach((str, count) -> {
            Lemma lemmaEntity;
            Index indexEntity;

            if (lemmaRepository.existsByLemma(str)) {
                lemmaEntity = lemmaRepository.findByLemmaAndSiteId(str, siteEntity);
                indexEntity = indexRepository.findByPageIdAndLemmaId(pageEntity, lemmaEntity);

                if (indexEntity == null) {
                    createAndSaveNewIndex(pageEntity, lemmaEntity, count);

                    repositoryManager.executeTransaction(() ->
                            lemmaRepository.incrementFrequency(lemmaEntity.getId())
                    );

                    return;
                }

                repositoryManager.executeTransaction(() ->
                        indexRepository.incrementRank(indexEntity.getId(), count)
                );

            } else {
                // TODO: Полностью убрать возможность создания дубликата леммы
                lemmaEntity = createAndSaveNewLemma(siteEntity, str);
                createAndSaveNewIndex(pageEntity, lemmaEntity, count);
            }
        });
    }


    /**
     * Метод принимает текст, разбивает его на слова, избавляется от служебных частей речи и возвращает
     * HashMap, где ключом является слово, а значением количество его повторений в тексте
     * @param text String
     * @return HashMap<String, Integer>
     */
    public HashMap<String, Integer> collectLemmas(String text) {

        if (text.isBlank()) {
            return new HashMap<>();
        }

        HashMap<String, Integer> result = new HashMap<>();
        LuceneMorphology luceneMorph = luceneMorphInitialization();
        String[] words = splitTextToWords(text);

        for (String word : words) {

            if (word.isBlank() || isFunctionalPartOfSpeech(luceneMorph, word)) {
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
     *
     * @param luceneMorph LuceneMorphology
     * @param word        Слово
     * @return boolean
     */
    private static boolean isFunctionalPartOfSpeech(LuceneMorphology luceneMorph, String word) {
        List<String> wordBaseForms = luceneMorph.getMorphInfo(word);
        String wordProps = wordBaseForms.get(0);

        for (String part : FUNCTIONAL_PARTS_OF_SPEECH) {
            if (wordProps.contains(part)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Метод принимает текст, переводит его в нижний регистр, а также удаляет все символы, которые не
     * являются символами русского алфавита или пробельным символом и заменяет все множественные пробельные
     * символы на один пробел. Затем идёт разделение текста на слова и возвращается массив строк.
     *
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
     *
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
     * Метод создаёт, сохраняет в базу данных в рамках одной транзакции и возвращает
     * новую сущность Lemma.
     *
     * @param site Сущность сайта
     * @param str  Строка с леммой
     * @return Lemma
     */
    private Lemma createAndSaveNewLemma(Site site, String str) {
        Lemma lemmaEntity = new Lemma();
        lemmaEntity.setSiteId(site);
        lemmaEntity.setLemma(str);
        lemmaEntity.setFrequency(1L);

        repositoryManager.executeTransaction(() ->
                lemmaRepository.save(lemmaEntity)
        );

        return lemmaEntity;
    }


    /**
     * Метод создаёт и сохраняет в базу данных новую сущность Index в рамках одной транзакции.
     *
     * @param page        Сущность страницы
     * @param lemmaEntity Сущность леммы
     * @param count       Количество повторений леммы на странице
     */
    private void createAndSaveNewIndex(Page page, Lemma lemmaEntity, Integer count) {
        Index indexEntity = new Index();
        indexEntity.setPageId(page);
        indexEntity.setLemmaId(lemmaEntity);
        indexEntity.setRank(count.floatValue());

        repositoryManager.executeTransaction(() ->
                indexRepository.save(indexEntity)
        );

    }


    /**
     * Метод проверяет, существует ли индексы у переданной в параметры страницы, если индексы существуют
     * то из базы данных удаляются все индексы связанные с этой страницы, а так же обновляются/удаляются
     * связанные леммы.
     *
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
}

