import com.delta.store.security.*;
import com.delta.store.security.oauth2.*;
import com.delta.store.user.repository.UserRepository;
import org.springframework.context.annotation.*;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.web.csrf.CsrfToken;
import jakarta.servlet.http.HttpServletRequest;
import java.net.*;
import java.net.http.*;
import java.lang.reflect.Proxy;
import java.util.*;

@Configuration(proxyBeanMethods=false)
@EnableAutoConfiguration(exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class)
@Import({SecurityConfig.class,SecurityHttpChecks.Endpoints.class})
public class SecurityHttpChecks {
 @Bean org.springframework.security.crypto.password.PasswordEncoder encoder(){return new BCryptPasswordEncoder();}
 @Bean UserRepository repo(){return (UserRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{UserRepository.class},(p,m,a)->switch(m.getName()){case "hashCode"->System.identityHashCode(p);case "equals"->p==a[0];case "toString"->"test-user-repository";default->Optional.empty();});}
 @Bean CustomUserDetailsService details(UserRepository r){return new CustomUserDetailsService(r){public UserDetails loadUserByUsername(String name){return org.springframework.security.core.userdetails.User.withUsername(name).password(new BCryptPasswordEncoder().encode("test-password")).roles(name.equals("admin")?"ADMIN":"USER").build();}};}
 @Bean CustomOAuth2UserService oauth(UserRepository r){return new CustomOAuth2UserService(r,new BCryptPasswordEncoder());}
 @Bean CustomOidcUserService oidc(CustomOAuth2UserService o){return new CustomOidcUserService(o);}
 @Bean ClientRegistrationRepository clients(){return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("test").clientId("test").clientSecret("test").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("{baseUrl}/login/oauth2/code/test").authorizationUri("https://example.invalid/auth").tokenUri("https://example.invalid/token").userInfoUri("https://example.invalid/user").userNameAttributeName("email").build());}
 @RestController static class Endpoints {
  @GetMapping("/shop") String shop(HttpServletRequest r){CsrfToken c=(CsrfToken)r.getAttribute(CsrfToken.class.getName());return c.getHeaderName()+":"+c.getToken();}
  @GetMapping("/admin/probe") String adminProbe(){return "ok";}
  @GetMapping("/api/products") String list(){return "products";}
  @PostMapping({"/cart/add","/checkout/paga","/api/products"}) String mutation(){return "ok";}
 }
 static int port,checks;
 static HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NEVER).build();}
 static HttpResponse<String> request(HttpClient c,String method,String path,String body,String[] token)throws Exception{
  var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path));
  if(token!=null)b.header(token[0],token[1]);
  if(method.equals("POST"))b.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body==null?"":body));else b.GET();
  return c.send(b.build(),HttpResponse.BodyHandlers.ofString());
 }
 static String[] token(HttpClient c)throws Exception{return request(c,"GET","/shop",null,null).body().split(":",2);}
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println("PASS "+s);checks++;}
 public static void main(String[] args)throws Exception{
  try(var context=new SpringApplication(SecurityHttpChecks.class).run("--server.port=0","--spring.main.banner-mode=off","--logging.level.root=ERROR")){
   port=((WebServerApplicationContext)context).getWebServer().getPort();var c=client();
   check(request(c,"GET","/api/products",null,null).statusCode()==200,"anonymous product reads allowed");
   check(request(c,"POST","/api/products","",null).statusCode()!=200,"anonymous product writes blocked");
   check(request(c,"POST","/cart/add","",null).statusCode()==403,"cart POST without CSRF denied");
   check(request(c,"POST","/checkout/paga","",null).statusCode()==403,"checkout POST without CSRF denied");
   check(request(c,"POST","/cart/add","",token(c)).statusCode()==200,"guest cart POST with CSRF allowed");
   var user=client();var t=token(user);request(user,"POST","/login","username=user&password=test-password",t);
   check(request(user,"POST","/api/products","",null).statusCode()==403,"authenticated customer cannot write catalog");
   check(request(user,"GET","/admin/probe",null,null).statusCode()==403,"customer cannot enter admin area");
   var admin=client();t=token(admin);request(admin,"POST","/login","username=admin&password=test-password",t);
   check(request(admin,"POST","/api/products","",null).statusCode()==200,"existing admin API write still allowed");
   check(request(admin,"GET","/admin/probe",null,null).statusCode()==200,"admin area still accessible to administrator");
   check(request(admin,"POST","/checkout/paga","",token(admin)).statusCode()==200,"valid checkout POST still passes filters");
   System.out.println("SUCCESS: "+checks+" HTTP security checks against real SecurityConfig");
  }
 }
}
