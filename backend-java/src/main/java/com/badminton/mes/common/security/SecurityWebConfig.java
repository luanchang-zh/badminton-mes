package com.badminton.mes.common.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 安全拦截配置：/api/** 默认全部要求登录，白名单仅登录接口。
 *
 * <p>新增免登录接口须在此显式登记，保持"默认拒绝"的安全基线(SEC-001)。
 *
 * @author 张竹灏
 * @date 2026/07/09
 */
@Configuration
public class SecurityWebConfig implements WebMvcConfigurer {

    /** 登录接口路径，唯一免登录白名单 */
    public static final String LOGIN_PATH = "/api/system/auth/login";

    /** 人脸登录路径 */
    public static final String FACE_LOGIN_PATH = "/api/system/auth/face_login";

    /** 账号密码登录、人脸登录、注册和注册职位查询公开路径 */
    public static final String[] AUTH_PUBLIC_PATHS = {
            LOGIN_PATH,
            FACE_LOGIN_PATH,
            "/api/system/auth/register",
            "/api/system/auth/registration_roles"
    };

    /** 微信小程序免登录路径，仅用于换取身份和首次绑定 */
    public static final String[] MINI_APP_PUBLIC_PATHS = {
            "/api/system/mini_app/auth/login",
            "/api/system/mini_app/auth/binding_codes/*/preview",
            "/api/system/mini_app/auth/bind_by_code"
    };

    private final AuthInterceptor authInterceptor;

    private final List<String> allowedOrigins;

    private final boolean authDisabled;

    /**
     * 构造器注入，依赖不可变。
     *
     * @param authInterceptor 登录鉴权拦截器
     * @param allowedOrigins  前端允许来源列表
     * @param authDisabled    是否关闭认证，仅允许本地联调环境启用
     */
    public SecurityWebConfig(AuthInterceptor authInterceptor,
                             @Value("${mes.web.cors.allowed-origins:http://localhost:5173}")
                             List<String> allowedOrigins,
                             @Value("${mes.security.auth-disabled:false}") boolean authDisabled) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.authDisabled = authDisabled;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 只对后端 API 开放跨域，并把来源限制在配置项中，避免使用通配符放大浏览器访问范围。
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                // 显式列出系统实际使用的请求方法和请求头，预检请求可据此快速完成权限判断。
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)
                // 系统通过 Authorization Bearer token 鉴权，不依赖跨域 Cookie，因此禁止携带凭据。
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 本地联调可通过配置关闭鉴权；生产配置保持 false，继续执行默认登录保护。
        if (authDisabled) {
            return;
        }

        // 采用“全部 API 先拦截、仅显式白名单放行”的方式，新增接口会自动继承登录校验。
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(AUTH_PUBLIC_PATHS)
                .excludePathPatterns(MINI_APP_PUBLIC_PATHS);
    }
}
