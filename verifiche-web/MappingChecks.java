import java.nio.file.*;
import org.hibernate.boot.*;
import org.hibernate.boot.registry.*;
public class MappingChecks {
 public static void main(String[] args) throws Exception {
  var registry=new StandardServiceRegistryBuilder()
    .applySetting("hibernate.dialect","org.hibernate.dialect.PostgreSQLDialect")
    .applySetting("hibernate.boot.allow_jdbc_metadata_access","false")
    .applySetting("hibernate.connection.provider_class","org.hibernate.engine.jdbc.connections.internal.UserSuppliedConnectionProviderImpl")
    .applySetting("hibernate.hbm2ddl.auto","none").build();
  try {
   var sources=new MetadataSources(registry);int count=0;
   try(var paths=Files.walk(Path.of("work/online-store/src/main/java"))){
    for(var p:paths.filter(p->p.toString().endsWith(".java")).toList()){
     String name=Path.of("work/online-store/src/main/java").relativize(p).toString().replace('/', '.').replace(".java", "");
     Class<?> type=Class.forName(name);
     if(type.isAnnotationPresent(jakarta.persistence.Entity.class)){sources.addAnnotatedClass(type);count++;}
    }
   }
   var metadata=sources.buildMetadata();
   ((org.hibernate.boot.spi.MetadataImplementor)metadata).validate();
   try(var factory=metadata.buildSessionFactory();var em=factory.createEntityManager()){
    for(Class<?> repo:new Class<?>[]{com.delta.store.web.payment.repository.CheckoutAttemptRepository.class,com.delta.store.order.repository.OrderMailJobRepository.class,com.delta.store.giftcard.repository.GiftCardRepository.class,com.delta.store.web.payment.repository.CheckoutSnapshotRepository.class}) {
     for(var method:repo.getMethods()) {var query=method.getAnnotation(org.springframework.data.jpa.repository.Query.class);if(query!=null && !query.nativeQuery())em.createQuery(query.value());}
    }
   }
   System.out.println("PASS: "+count+" entity mappings and new HQL queries validated without database access");
  } finally {StandardServiceRegistryBuilder.destroy(registry);}
 }
}
