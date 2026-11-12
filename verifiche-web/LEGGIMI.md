# Controlli supplementari

I file Java di questa cartella sono harness indipendenti di verifica, non classi dell’applicazione e non vengono inclusi nel classpath di produzione.

La suite di regressione ordinaria è in src/test/java/com/delta/store/web ed è richiamata dal test JUnit.

MappingChecks valida le entità con Hibernate senza DB; SecurityHttpChecks avvia solo un contesto HTTP di prova con la SecurityConfig reale, utenze fittizie e controller minimi. Non dimostrano avvio dell’intero progetto o isolamento delle transazioni sul database.

I log inclusi documentano l’esecuzione svolta nell’ambiente di lavoro, usando le librerie del JAR allegato alla baseline. Maven Central non era raggiungibile.
