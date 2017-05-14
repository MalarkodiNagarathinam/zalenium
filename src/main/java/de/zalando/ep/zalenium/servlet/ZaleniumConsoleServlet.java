package de.zalando.ep.zalenium.servlet;

import com.google.common.io.ByteStreams;
import org.openqa.grid.internal.Registry;
import org.openqa.grid.internal.RemoteProxy;
import org.openqa.grid.internal.utils.configuration.GridHubConfiguration;
import org.openqa.grid.web.servlet.RegistryBasedServlet;
import org.openqa.selenium.internal.BuildInfo;
import org.openqa.selenium.remote.DesiredCapabilities;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
    Taken from the original org.openqa.grid.web.servlet.beta.ConsoleServlet
 */
public class ZaleniumConsoleServlet extends RegistryBasedServlet {
    private static String coreVersion;

    public ZaleniumConsoleServlet() {
        this(null);
    }

    public ZaleniumConsoleServlet(Registry registry) {
        super(registry);
        coreVersion = new BuildInfo().getReleaseLabel();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        process(request, response);
    }

    protected void process(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        int refresh = -1;
        if (request.getParameter("refresh") != null) {
            refresh = Integer.parseInt(request.getParameter("refresh"));
        }

        List<String> nodes = new ArrayList<>();
        for (RemoteProxy proxy : getRegistry().getAllProxies()) {
            nodes.add(proxy.getHtmlRender().renderSummary());
        }

        int size = nodes.size();
        int rightColumnSize = size / 2;
        int leftColumnSize = size - rightColumnSize;

        StringBuilder leftColumnNodes = new StringBuilder();
        for (int i = 0; i < leftColumnSize; i++) {
            leftColumnNodes.append(nodes.get(i));
        }
        StringBuilder rightColumnNodes = new StringBuilder();
        for (int i = leftColumnSize; i < nodes.size(); i++) {
            rightColumnNodes.append(nodes.get(i));
        }


        Map<String, String> consoleValues = new HashMap<>();
        consoleValues.put("{{refreshInterval}}", String.valueOf(refresh));
        consoleValues.put("{{coreVersion}}", coreVersion);
        consoleValues.put("{{leftColumnNodes}}", leftColumnNodes.toString());
        consoleValues.put("{{rightColumnNodes}}", rightColumnNodes.toString());
        consoleValues.put("{{requestQueue}}", getRequestQueue().toString());
        if (request.getParameter("config") != null) {
            consoleValues.put("{{hubConfig}}", getConfigInfo(request.getParameter("configDebug") != null));
        } else {
            consoleValues.put("{{hubConfig}}", "<a href='?config=true&configDebug=true'>view config</a>");
        }

        String templateFile = "html_templates/zalenium_console_servlet.html";
        TemplateRenderer templateRenderer = new TemplateRenderer(templateFile);
        String renderTemplate = templateRenderer.renderTemplate(consoleValues);


        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(200);
        try (InputStream in = new ByteArrayInputStream(renderTemplate.getBytes("UTF-8"))) {
            ByteStreams.copy(in, response.getOutputStream());
        } finally {
            response.getOutputStream().close();
        }
    }

    private Object getRequestQueue() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div>");
        int numUnprocessedRequests = getRegistry().getNewSessionRequestCount();

        if (numUnprocessedRequests > 0) {
            builder.append(String.format("%d requests waiting for a slot to be free.",
                    numUnprocessedRequests));
        }

        builder.append("<ul>");
        for (DesiredCapabilities req : getRegistry().getDesiredCapabilities()) {
            builder.append("<li>").append(req).append("</li>");
        }
        builder.append("</ul>");
        builder.append("</div>");
        return builder.toString();
    }

    /**
     * retracing how the hub config was built to help debugging.
     *
     * @return html representation of the hub config
     */
    private String getConfigInfo(boolean verbose) {

        StringBuilder builder = new StringBuilder();

        GridHubConfiguration config = getRegistry().getConfiguration();
        builder.append("<div  id='hub-config'>");
        builder.append("<b>Config for the hub :</b><br/>");
        builder.append(prettyHtmlPrint(config));

        if (verbose) {

            GridHubConfiguration tmp = new GridHubConfiguration();

            builder.append("<b>Config details :</b><br/>");
            builder.append("<b>hub launched with :</b>");
            builder.append(config.toString());

            builder.append("<br/><b>the final configuration comes from :</b><br/>");
            builder.append("<b>the default :</b><br/>");
            builder.append(prettyHtmlPrint(tmp));

            builder.append("<br/><b>updated with params :</b></br>");
            tmp.merge(config);
            builder.append(prettyHtmlPrint(tmp));
        }
        builder.append("</div>");
        return builder.toString();
    }

    private String prettyHtmlPrint(GridHubConfiguration config) {
        return config.toString("<abbr title='%1$s'>%1$s : </abbr>%2$s</br>");
    }

}
