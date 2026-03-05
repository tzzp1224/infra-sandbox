package com.demo.memory;

import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final RedisTemplate<String, String> redisTemplate;

    public ChatController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody ChatRequest request) {
        String key = "chat:history:" + request.userId();

        // Redis 操作入口：从 RedisTemplate 获取 ListOperations 视图，用于把 Java 的 List 语义映射到 Redis 的 List 命令。
        // 这一步只是拿到操作句柄，不会立刻发网络请求；真正访问 Redis 的时机在后续调用 range/rightPush 等方法时。
        ListOperations<String, String> listOps = redisTemplate.opsForList();

        // Redis 读操作：执行 LRANGE key 0 -1，读取该 userId 当前整段历史消息。
        // 底层会由 Redis 单线程按 key 查找 List 对象并返回快照；0 到 -1 代表从头到尾全部元素。
        List<String> oldHistory = listOps.range(key, 0, -1);

        // Redis 写操作：执行 RPUSH key message，把新消息追加到 List 尾部。
        // RPUSH 是 O(1) 均摊复杂度，适合时间顺序追加；如果 key 不存在，Redis 会自动创建该 List。
        listOps.rightPush(key, request.message());

        // Redis 过期操作：执行 EXPIRE key 60，每次新消息到来都把 TTL 刷新为 60 秒。
        // 这相当于滑动过期窗口：只要 60 秒内有新消息，key 就会继续存活；超过 60 秒无写入则自动删除。
        redisTemplate.expire(key, Duration.ofSeconds(60));

        // Redis 再读操作：再次执行 LRANGE key 0 -1，拿到“追加后”的完整历史并作为接口返回值。
        // 这里重新读取而不是在 Java 本地拼接，可确保返回结果和 Redis 中最终状态一致。
        List<String> latestHistory = listOps.range(key, 0, -1);
        List<String> historyToReturn;
        if (latestHistory == null) {
            List<String> fallback = oldHistory == null ? new ArrayList<>() : new ArrayList<>(oldHistory);
            fallback.add(request.message());
            historyToReturn = fallback;
        } else {
            historyToReturn = latestHistory;
        }

        return Map.of(
                "userId", request.userId(),
                "history", historyToReturn
        );
    }

    public record ChatRequest(String userId, String message) {
    }
}
