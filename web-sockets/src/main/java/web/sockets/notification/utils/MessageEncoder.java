package web.sockets.notification.utils;


import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;
import com.fasterxml.jackson.databind.ObjectMapper;

public class MessageEncoder implements Encoder.Text<NotificationContentDto> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String encode(NotificationContentDto message) throws EncodeException {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new EncodeException(message, "Object encoding error", e);
        }
    }

    @Override
    public void init(EndpointConfig config) {
        // No initialization required for this encoder
    }

    @Override
    public void destroy() {
        // Cleanup if necessary
    }
}


