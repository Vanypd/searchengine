package searchengine.services.web.html;

import lombok.NonNull;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import searchengine.model.implementation.Page;
import searchengine.repository.implementation.SiteRepository;
import searchengine.services.utils.URLParser;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public class HTMLManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HTMLManager.class);


    private HTMLManager() {}


    /**
     * Метод создаёт, заполняет и возвращает экземпляр Page. Под заполнением подразумевается получение
     * статуса страницы, её содержимого, пути из переданного url и указание Site к которому принадлежит
     * данная страница
     * @param url Ссылка на страницу
     * @param siteRepository JPA-репозиторий объекта Site
     * @return Page - Объект страницы
     */
    public static Page getPageEntity(@NonNull URL url, @NonNull SiteRepository siteRepository) {

        String baseUrl = URLParser.getBaseUrl(url);
        String path = URLParser.getPathFromUrl(baseUrl, url);
        Page page = new Page();
        AtomicReference<String> html = new AtomicReference<>("");

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            ClassicHttpRequest httpGet = ClassicRequestBuilder.get(url.toString()).build();
            httpGet.addHeader("User-Agent", "DysonSearchBot");
            httpGet.addHeader("Referer", "http://www.google.com");

            httpclient.execute(httpGet, response -> {
                html.set(EntityUtils.toString(response.getEntity()));
                page.setCode(response.getCode());
                page.setPath(path);
                page.setContent(html.get());
                page.setSiteId(siteRepository.findByUrl(baseUrl));
                return null;
            });


        } catch (IOException e) {
            LOGGER.error("Ошибка при попытке собрать сущность Page", e);
            throw new RuntimeException(e);
        }

        return page;
    }


    /**
     * Метод принимает ссылку, через которую делает обход по всей странице и возвращает список
     * найденных уникальных внутренних ссылок. Все ссылки добавляются в нижнем регистре.
     * @param page Сущность страницы, содержащая html-страницу
     * @return Set ссылок найденных на странице
     */
    public static Set<String> getPagePaths(@NonNull Page page) {

        String baseUrl = page.getSiteId().getUrl();
        Set<String> foundedPageLinks = new HashSet<>();
        Document doc = Jsoup.parse(page.getContent());
        Elements links = doc.select("a");

        String regex = "(" + Pattern.quote(baseUrl) + ")?(/[^,.\\s\"]+)(\\.html|\\.htm)?";

        for (Element link : links) {
            String href = link.attr("href");

            if (!href.matches(regex)) {
                continue;
            }
            if (href.endsWith("/")) {
                href = href.substring(0, href.length() - 1);
            }
            if (href.startsWith(baseUrl)) {
                href = href.substring(baseUrl.length());
            }

            foundedPageLinks.add(href.toLowerCase());
        }

        return foundedPageLinks;
    }


    /**
     * Метод, который принимает HTML-страницу и возвращает её содержимое без HTML-тегов.
     * @param html Строка HTML-страницы содержащая HTML-теги
     * @return Строка с содержимым страницы без HTML-тегов
     */
    public static String getTextFromHTML(@NonNull String html) {
        Document doc = Jsoup.parse(html);
        return getTextFromHTML(doc);
    }


    /**
     * Метод, который принимает HTML-страницу и возвращает её содержимое без HTML-тегов.
     * @param doc Document JSoup
     * @return Строка с содержимым страницы без HTML-тегов
     */
    public static String getTextFromHTML(@NonNull Document doc) {
        return doc.text();
    }


    /**
     * Метод принимает строковый HTML и возвращает заголовок страницы
     * @param content String - HTML-контент
     * @return String - заголовок страницы
     */
    public static String getTitleFromContent(String content) {
        Document doc = Jsoup.parse(content);
        return doc.title();
    }
}