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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class HTMLManager {
    private static final Logger logger = LoggerFactory.getLogger(HTMLManager.class);


    private HTMLManager() {}


    public static Document getHTMLDocument(@NonNull URL url) {
        Document doc = null;

        try {
            doc = Jsoup.connect(url.toString()).get();
        } catch (IOException e) {
            logger.error("Ошибка при попытке получить HTML с помощью JSoup", e);
        }

        return doc;
    }


    public static Page getPageEntity(@NonNull URL url, @NonNull SiteRepository siteRepository) {

        String baseUrl = getBaseUrl(url);
        String path = getUrlPath(baseUrl, url);
        Page page = new Page();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            ClassicHttpRequest httpGet = ClassicRequestBuilder.get(url.toString()).build();
            httpGet.addHeader("User-Agent", "DysonSearchBot");
            httpGet.addHeader("Referer", "http://www.google.com");

            httpclient.execute(httpGet, response -> {
                page.setCode(response.getCode());
                page.setPath(path);
                page.setContent(EntityUtils.toString(response.getEntity()));
                page.setSiteId(siteRepository.findByUrl(baseUrl));
                return null;
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return page;
    }


    public static Set<String> getPagePaths(@NonNull URL url) {
        String baseUrl = getBaseUrl(url);
        Set<String> foundedPageLinks = new HashSet<>();
        Document doc = getHTMLDocument(url);
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

            foundedPageLinks.add(href);
        }

        return foundedPageLinks;
    }


    public static String getBaseUrl(URL url) {
        String protocol = url.getProtocol();
        String authority = url.getAuthority();

        if (protocol == null || authority == null) {
            String errorMessage = "Передан URL без базовой части";
            Exception e = new Exception(errorMessage);
            logger.error(errorMessage, e);
            try {
                throw e;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        return protocol + "://" + authority;
    }


    public static String getUrlPath(String baseUrl, URL url) {
        if (baseUrl.equals(url.toString())) {
            return  "/";
        }
        else {
            return url.toString().substring(baseUrl.length());
        }
    }


    public static URL mapStringToUrl(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            logger.error("Передан URL в неверном формате", e);
            throw new RuntimeException(e);
        }
    }


    public static URL concatBaseUrlWithPath(String baseUrl, String path) {
        try {
            return new URL(baseUrl + path);
        } catch (MalformedURLException e) {
            logger.error("Ошибка при попытке конкатенации базовой части URL и его Path", e);
            throw new RuntimeException(e);
        }
    }
}