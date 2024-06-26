package Project.PetWalk.handler;

import Project.PetWalk.dto.ChatMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChatHandler extends TextWebSocketHandler {
    private final ObjectMapper mapper;

    // 현재 연결된 세션들
    private final Set<WebSocketSession> sessions = new HashSet<>();

    // chatRoomId: {session1, session2}
    private final Map<String, Set<WebSocketSession>> chatRoomSessionMap = new HashMap<>();

    // 소켓 연결 확인
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("{} 연결됨", session.getId());
        sessions.add(session);
    }

    // 소켓 통신 시 메세지의 전송을 다루는 부분
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            log.info("payload {}", payload);

            // 페이로드 -> chatMessageDto로 변환
            ChatMessageDto chatMessageDto = mapper.readValue(payload, ChatMessageDto.class);
            log.info("session {}", chatMessageDto.toString());

            String chatRoomId = chatMessageDto.getRoomId();
            if (!chatRoomSessionMap.containsKey(chatRoomId)) {
                chatRoomSessionMap.put(chatRoomId, new HashSet<>());
            }
            Set<WebSocketSession> chatRoomSession = chatRoomSessionMap.get(chatRoomId);

            if (chatMessageDto.getType().equals(ChatMessageDto.MessageType.ENTER)) {
                chatRoomSession.add(session);
            }
            if (chatRoomSession.size() >= 3) {
                removeClosedSession(chatRoomSession);
            }
            sendMessageToChatRoom(chatMessageDto, chatRoomSession);
        } catch (Exception e) {
            log.error("Error handling text message", e);
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    // 소켓 종료 확인
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("{} 연결 끊김", session.getId());
        sessions.remove(session);
    }

    private void removeClosedSession(Set<WebSocketSession> chatRoomSession) {
        chatRoomSession.removeIf(sess -> !sessions.contains(sess));
    }

    private void sendMessageToChatRoom(ChatMessageDto chatMessageDto, Set<WebSocketSession> chatRoomSession) {
        chatRoomSession.parallelStream().forEach(sess -> sendMessage(sess, chatMessageDto));
    }

    public <T> void sendMessage(WebSocketSession session, T message) {
        try {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }
}