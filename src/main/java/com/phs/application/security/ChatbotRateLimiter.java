package com.phs.application.security;

import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiter cho /api/chatbot — chong abuse + dot quota Groq.
 *
 * Co che: sliding window 60 giay theo IP.
 * Mac dinh: toi da 20 request/phut moi IP.
 */
@Component
public class ChatbotRateLimiter {

    private static final int MAX_REQUESTS_PER_WINDOW = 20;
    private static final long WINDOW_MILLIS = 60_000L;
    private static final long CLEANUP_INTERVAL = 300_000L;  // 5 phut

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    public boolean tryAcquire(HttpServletRequest request) {
        String ip = clientIp(request);
        long now = System.currentTimeMillis();

        // Periodic cleanup buckets cu de tranh memory leak
        if (now - lastCleanup > CLEANUP_INTERVAL) {
            cleanup(now);
            lastCleanup = now;
        }

        Bucket bucket = buckets.computeIfAbsent(ip, k -> new Bucket(now));
        synchronized (bucket) {
            // Reset neu het window
            if (now - bucket.windowStart > WINDOW_MILLIS) {
                bucket.windowStart = now;
                bucket.count.set(0);
            }
            if (bucket.count.get() >= MAX_REQUESTS_PER_WINDOW) {
                return false;
            }
            bucket.count.incrementAndGet();
            return true;
        }
    }

    public int getRemaining(HttpServletRequest request) {
        Bucket bucket = buckets.get(clientIp(request));
        if (bucket == null) return MAX_REQUESTS_PER_WINDOW;
        long now = System.currentTimeMillis();
        if (now - bucket.windowStart > WINDOW_MILLIS) return MAX_REQUESTS_PER_WINDOW;
        return Math.max(0, MAX_REQUESTS_PER_WINDOW - bucket.count.get());
    }

    private void cleanup(long now) {
        buckets.entrySet().removeIf(e -> now - e.getValue().windowStart > WINDOW_MILLIS * 2);
    }

    private String clientIp(HttpServletRequest request) {
        String header = request.getHeader("X-Forwarded-For");
        if (header != null && !header.isEmpty()) {
            // Lay IP dau tien neu co nhieu proxy
            int comma = header.indexOf(',');
            return (comma > 0 ? header.substring(0, comma) : header).trim();
        }
        header = request.getHeader("X-Real-IP");
        if (header != null && !header.isEmpty()) return header.trim();
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private static class Bucket {
        volatile long windowStart;
        final AtomicInteger count = new AtomicInteger(0);

        Bucket(long start) { this.windowStart = start; }
    }
}
