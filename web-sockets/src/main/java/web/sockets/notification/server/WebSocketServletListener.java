package web.sockets.notification.server;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.websocket.server.ServerContainer;
@WebListener
public class WebSocketServletListener implements ServletContextListener  {
    private static final Logger log = LogManager.getLogger(WebSocketServletListener.class);

    @Override
    public void contextInitialized(ServletContextEvent event) {
        //log.error("contextInitialized: Starting WebSocket initialization");

        ServletContext context = event.getServletContext();
        //log.error("ServletContext: " + context);

        try {
            //log.error("Attempting to initialize WebSocket container");

            ServerContainer serverContainer = (ServerContainer) context.getAttribute("javax.websocket.server.ServerContainer");

            if (serverContainer != null) {
                //log.error("WebSocket ServerContainer found, registering WebSocket endpoint");
                serverContainer.addEndpoint(WebSocketEndPoint.class);
                //log.error("WebSocket endpoint registered successfully");
            } else {
                //log.error("WebSocket ServerContainer is null, WebSocket server not initialized.");
            }
        } catch (Exception e) {
            log.error("Exception during WebSocket initialization", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // Optional cleanup when the servlet context is destroyed
        //log.error("contextDestroyed: WebSocket context destroyed");
    }
}

