package com.delta.store.web.payment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
@Component
public class CheckoutRateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String,long[]> buckets=new ConcurrentHashMap<>();
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getServletPath();
        boolean start=path.equals("/checkout/paga") || path.equals("/checkout/salva-spedizione");
        boolean code=path.equals("/checkout/applica-giftcard") || path.equals("/checkout/applica-sconto");
        if("POST".equals(request.getMethod()) && (start||code)) {
            long now=System.currentTimeMillis();String key=request.getRemoteAddr()+":"+(start?"pay":"code");
            if(buckets.size()>10000)buckets.entrySet().removeIf(e->now-e.getValue()[0]>600000);
            if(buckets.size()>10000 && !buckets.containsKey(key)){response.sendError(429,"Attendi prima di riprovare");return;}
            long[] bucket=buckets.computeIfAbsent(key,k->new long[]{now,0});
            synchronized(bucket){if(now-bucket[0]>600000){bucket[0]=now;bucket[1]=0;}if(++bucket[1]>(start?10:30)){response.setHeader("Retry-After","600");response.sendError(429,"Troppi tentativi: attendi prima di riprovare");return;}}
        }
        chain.doFilter(request,response);
    }
}
