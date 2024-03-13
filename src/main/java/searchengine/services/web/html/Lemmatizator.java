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
import java.util.Arrays;
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
        checkIndexExistence(page);

        lemmas.forEach((str, count) -> {
            Lemma lemma = lemmaRepository.findByLemma(str);

            if (lemma == null) {
                Lemma lemmaEntity = createNewLemmaEntity(site, str);
                createNewIndexEntity(page, lemmaEntity, count);
            }
            else {
                Index index = indexRepository.findByPageIdAndLemmaId(page, lemma);

                if (index == null) {
                    String errorMessage = "index == null, в базе данных содержится lemma без индекса";
                    Exception e = new NullPointerException(errorMessage);
                    LOGGER.error(errorMessage, e);
                    return;
                    // TODO: Связать таблицу
                }

                lemma.setFrequency(lemma.getFrequency() + 1);
                index.setRank(index.getRank() + count);

                repositoryManager.executeTransaction(() -> {
                    lemmaRepository.save(lemma);
                    indexRepository.save(index);
                });
            }
        });
    }


    private static HashMap<String, Integer> collectLemmas(String text) {

        if (text.isBlank()) {
            return new HashMap<>();
        }

        HashMap<String, Integer> result = new HashMap<>();
        LuceneMorphology luceneMorph = luceneMorphInitialization();

        String[] words = splitTextToWords(text);
        System.out.println(Arrays.toString(words));

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


    private static boolean isFunctionalPartOfSpeech(String wordWithProps) {
        for (String part : functionalPartsOfSpeech) {
            if (wordWithProps.contains(part)) {
              return true;
            }
        }
        return false;
    }


    private static String[] splitTextToWords(String text) {
        return text.trim()
                .toLowerCase()
                .replaceAll("[^а-яё\\s]", "")
                .replaceAll("\\s+", " ")
                .split("\\s");
    }


    private static LuceneMorphology luceneMorphInitialization() {
        try {
            return new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private Lemma createNewLemmaEntity(Site site, String str) {
        Lemma lemmaEntity = new Lemma();
        lemmaEntity.setSiteId(site);
        lemmaEntity.setLemma(str);
        lemmaEntity.setFrequency(1L);

        repositoryManager.executeTransaction(() ->
                lemmaRepository.save(lemmaEntity)
        );

        return lemmaEntity;
    }


    private void createNewIndexEntity(Page page, Lemma lemmaEntity, Integer count) {
        Index indexEntity = new Index();
        indexEntity.setPageId(page);
        indexEntity.setLemmaId(lemmaEntity);
        indexEntity.setRank(count.floatValue());

        repositoryManager.executeTransaction(() ->
                indexRepository.save(indexEntity)
        );
    }


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

