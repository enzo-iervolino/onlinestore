# Gestione spese di spedizione — 11 novembre 2026

Baseline: online-store (6).zip. Gestione adattata da Saierleggero.zip.

## Uso
Accedi come amministratore e apri Tabelle → Costi spedizione,
oppure /admin/shipping-costs. Puoi creare, modificare, filtrare,
attivare/disattivare ed eliminare le fasce.

Esempio (da configurare, nessuna tariffa viene inserita automaticamente):
- Minimo 0, massimo 49.99, costo 5.90.
- Minimo 50, massimo vuoto, costo 0 (gratuita).

I limiti sono inclusivi; le fasce attive non possono sovrapporsi.
Il massimo vuoto indica nessun limite. Importi non negativi, massimo due decimali.
Il calcolo usa il subtotale prodotti prima di sconti e gift card, come nella baseline.
Se nessuna fascia attiva corrisponde, il costo resta zero, come prima.
Gli ordini già registrati mantengono il costo salvato.

## Installazione
Importa l'intera cartella come progetto Maven in STS e compila con Java 17.
Il file ZIP contiene sorgenti, template, configurazioni e tutti i media originali.
Sono esclusi target, cronologia Git e metadati IDE: verranno rigenerati dove necessario.
Non sostituire il database di produzione.
Per un aggiornamento tramite solo JAR sulla VPS, aggiungi anche la voce
shipping-costs presente in config/menu/admin-menu.json al file menu esterno
usato dal servizio, mantenendo le altre personalizzazioni. Copiare il solo JAR
non aggiorna un menu esterno già esistente.

## Verifiche
- 16 controlli isolati eseguiti sul servizio Java: importi nulli/zero/negativi,
  soglie, gratuità, assenza di fascia, fasce inattive e sovrapposizioni.
  Il controllo usa sostituti delle dipendenze framework/repository, non un DB reale.
- Verificata assenza di classi e voci menu duplicate.
- Checkout, pagamenti, carrello e ordini confrontati byte per byte con la baseline:
  nessuna modifica ai relativi sorgenti Java.
- Aggiunti 5 test JUnit in ShippingManagementTest, da eseguire con Maven.
- Build Maven completa e test JUnit NON eseguiti: Maven Central non raggiungibile
  da questo ambiente (risoluzione DNS delle dipendenze fallita).
  In STS eseguire Maven Update Project e `mvn test` / `mvn clean package`.

## File modificati o aggiunti
- src/main/resources/default-config/admin-menu.json
- src/main/resources/templates/admin/shipping-costs/form.html
- src/main/resources/templates/admin/shipping-costs/list.html
- src/main/java/com/delta/store/security/SecurityConfig.java
- src/main/java/com/delta/store/web/admin/AdminShippingCostController.java
- src/main/java/com/delta/store/shipping/repository/ShippingCostRepository.java
- src/main/java/com/delta/store/shipping/model/ShippingCost.java
- src/main/java/com/delta/store/shipping/service/ShippingServiceImpl.java
- src/main/java/com/delta/store/shipping/service/ShippingService.java
- src/test/java/com/delta/store/shipping/ShippingManagementTest.java
- config/menu/admin-menu.json
