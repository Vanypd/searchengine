package searchengine.services.utils;

import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;

public class URLParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(URLParser.class);


    private URLParser() {}


    /**
     * Метод принимает ссылку и извлекает из неё базовую часть
     * @param url Ссылка в виде URL-объекта, пример: "https://example.com/path"
     * @return String - Строка базового url
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    public static String getBaseUrl(@NonNull URL url) {
        String protocol = url.getProtocol();
        String authority = url.getAuthority();

        if (protocol == null || authority == null) {
            String errorMessage = "Передан URL без базовой части";
            Exception e = new Exception(errorMessage);
            LOGGER.error(errorMessage, e);
            try {
                throw e;
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        return protocol + "://" + authority;
    }


    /**
     * Метод принимает базовый url и ссылку, а затем возвращает путь
     * @param baseUrl Базовый url, к примеру: "https://example.com"
     * @param url Ссылка из которой извлекается путь, например "https://example.com/path"
     * @return String - Строка пути
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    public static String getPathFromUrl(@NonNull String baseUrl, @NonNull URL url) {

        if (!url.toString().contains(baseUrl)) {
            String errorMessage = "Url не содержит переданный в параметры базовый url";
            IllegalArgumentException e = new IllegalArgumentException(errorMessage);
            LOGGER.error(errorMessage, e);
            throw e;
        }


        if (baseUrl.equals(url.toString())) {
            return  "/";
        }
        else {
            return url.toString().substring(baseUrl.length());
        }
    }


    /**
     * Преобразует строку в объект URL
     * @param s Преобразуемая строка
     * @return URL
     */
    public static URL mapStringToUrl(String s) {
        try {
            return new URL(s);
        } catch (MalformedURLException e) {
            LOGGER.error("Передан URL в неверном формате", e);
            throw new RuntimeException(e);
        }
    }


    /**
     * Метод принимает базовый url и путь, конкатинирует их, а затем возвращает в виде объекта URL
     * @param baseUrl Базовый URL, например: "https://example.com"
     * @param path Путь, например: "/path"
     * @return Ссылка в виде объекта URL
     */
    @SuppressWarnings("JavadocLinkAsPlainText")
    public static URL concatBaseUrlWithPath(String baseUrl, String path) {
        try {
            return new URL(baseUrl + path);
        } catch (MalformedURLException e) {
            LOGGER.error("Ошибка при попытке конкатенации базовой части URL и его Path", e);
            throw new RuntimeException(e);
        }
    }
}
