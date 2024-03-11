package searchengine.services.web.html;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import searchengine.services.utils.URLParser;

public class HTMLManagerTest {


    @Test
    @DisplayName("Преобразование строки в URL")
    public void mapStringToUrlTest() {
        String s = "https://example.com";
        String actual = URLParser.mapStringToUrl(s).toString();
        Assertions.assertEquals(s, actual);
    }


    @Test
    @DisplayName("Конкатенация базового url с path")
    public void concatBaseUrlWithPathTest() {
        String baseUrl = "https://example.com";
        String path = "/path";
        String actual = URLParser.concatBaseUrlWithPath(baseUrl, path).toString();
        Assertions.assertEquals("https://example.com/path", actual);
    }
}
