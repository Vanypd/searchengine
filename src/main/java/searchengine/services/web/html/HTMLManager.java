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
import searchengine.model.implementation.Page;
import searchengine.repository.implementation.SiteRepository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HTMLManager {


    private HTMLManager() {}


    public static Document getHTMLDocument(String url) {
        Document doc = null;

        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return doc;
    }


    public static Page getPageEntity(String domain, SiteRepository siteRepository) {
        return getPageEntity(domain, "", siteRepository);
    }


    public static Page getPageEntity(String domain, String path, SiteRepository siteRepository) {
        String url = domain + path;
        Page page = new Page();

        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            ClassicHttpRequest httpGet = ClassicRequestBuilder.get(url).build();
            httpGet.addHeader("User-Agent", "DysonSearchBot");
            httpGet.addHeader("Referer", "http://www.google.com");

            httpclient.execute(httpGet, response -> {
                page.setCode(response.getCode());
                page.setPath(url);
                page.setContent(EntityUtils.toString(response.getEntity()));
                page.setSiteId(siteRepository.findByUrl(domain));
                return null;
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return page;
    }


    public static List<String> getPagePaths(@NonNull String domain, @NonNull String url) {
        List<String> foundedPageLinks = new ArrayList<>();
        Document doc = getHTMLDocument(url);
        Elements links = doc.select("a");

        String regex = "(" + Pattern.quote(domain) + ")?(/[^,.\\s\"]+)(\\.html|\\.htm)?";

        for (Element link : links) {
            String href = link.attr("href");

            if (!href.matches(regex)) {
                continue;
            }
            if (href.endsWith("/")) {
                href = href.substring(0, href.length() - 1);
            }
            if (href.startsWith(domain)) {
                href = href.substring(domain.length());
            }

            foundedPageLinks.add(href);
        }

        return foundedPageLinks;
    }
}