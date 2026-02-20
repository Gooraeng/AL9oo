package com.back.global.security;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

/**
 * SecurityFilterChain의 필터 순서를 확인하는 테스트.
 * 실제 필터 순서를 콘솔에 출력합니다.
 */
//@SpringBootTest
class SecurityFilterOrderTest {

    @Qualifier("filterChain")
//    @Autowired
    private SecurityFilterChain securityFilterChain;

//    @Test
    void printFilterOrder() {
        List<Filter> filters = securityFilterChain.getFilters();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("Security Filter Chain Order (Total: " + filters.size() + " filters)");
        System.out.println("=".repeat(80));

        for (int i = 0; i < filters.size(); i++) {
            String filterName = filters.get(i).getClass().getSimpleName();
            String highlight = "";

            // 관심 있는 필터 하이라이트
            if (filterName.contains("OAuth2") || filterName.contains("Jwt") ||
                filterName.contains("UsernamePassword") || filterName.contains("Logout")) {
                highlight = " <<<";
            }

            System.out.printf("[%2d] %s%s%n", i + 1, filterName, highlight);
        }

        System.out.println("=".repeat(80) + "\n");
    }
}
