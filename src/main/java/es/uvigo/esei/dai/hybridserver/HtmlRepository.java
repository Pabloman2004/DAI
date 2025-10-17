package es.uvigo.esei.dai.hybridserver;

import java.io.IOException;


import es.uvigo.esei.dai.hybridserver.http.HTTPRequest;


public interface HtmlRepository extends AutoCloseable {
    String getPage(HTTPRequest req) throws IOException;
    String postPage(HTTPRequest req) throws IOException;
    String deletePage(HTTPRequest req) throws IOException;
    boolean exists(String path);

    @Override
    default void close() throws Exception {}
}
