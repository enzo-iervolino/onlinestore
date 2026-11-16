# Stock varianti — 16 novembre 2026
Baseline: online-store (4).zip allegato il 16/11/2026.

La quantità del prodotto con varianti è la somma delle quantità delle varianti, comprese quelle a zero; NULL vale zero. PostgreSQL mantiene products.stock_qty nella stessa transazione di ogni inserimento, modifica, cambio padre o eliminazione di variante. Include prenotazione checkout, rilascio prenotazione e scarico ordine tramite i percorsi esistenti, senza cambiare endpoint o scaricare due volte.
Il movimento viene annullato insieme al totale in caso di rollback. I movimenti dello stesso padre vengono serializzati con lock. Eliminando l'ultima variante il totale diventa zero; da quel momento torna modificabile manualmente.

Al primo avvio il nuovo initializer installa i trigger e riallinea i prodotti già presenti. Richiede PostgreSQL e permessi CREATE FUNCTION/TRIGGER sulle tabelle del progetto. Non serve lanciare una migrazione separata. L'avvio fallisce se l'installazione non riesce, per evitare di usare totali non affidabili. Fare il consueto backup DB prima del deploy.
La scheda con varianti ha stock in sola lettura. Nuove combinazioni generate partono da zero: assegnare le quantità effettive alle varianti, senza copiare il totale del padre in ciascuna. Prezzi ereditati come prima.
Le pagine già aperte richiedono ricaricamento per vedere movimenti di altre sessioni: aggiornamento transazionale del database, nessun push automatico nel browser.

File modificati: AdminProductController.java e templates/admin/products/product-form.html.
File aggiunti: ProductStockSchemaInitializer.java, db/product-stock.sql, docs/test-stock-varianti.sql e questo documento. Nessuna modifica agli altri file della baseline.

Verifiche: controllo statico dei percorsi stock, prenotazione/rilascio checkout e CRUD varianti. Non eseguiti build Maven e test PostgreSQL: runtime privo di Maven/JDK e server PostgreSQL.
Sul proprio ambiente: ./mvnw test; psql -v ON_ERROR_STOP=1 -d NOME_DATABASE -f docs/test-stock-varianti.sql (oppure mvnw.cmd test su Windows). Il test SQL usa tabelle temporanee e rollback. Verificare anche due checkout simultanei su varianti dello stesso prodotto.
ZIP completo di sorgenti, template, configurazioni e media esistenti; esclusi .git, target e metadati IDE.


## Correzione successiva alla segnalazione
La lettura di Product.stockQty ora usa un campo Hibernate @Formula con SUM delle varianti, senza inizializzare collezioni lazy. Il vecchio campo manuale viene usato solo se non esistono varianti. Questo rende il totale visualizzato indipendente dalla copia memorizzata nel padre alla lettura del prodotto.
Le schede amministrative modifica e visualizzazione aggiornano il totale ogni 5 secondi tramite l'endpoint varianti già esistente, senza cambiarne la risposta. I prodotti senza varianti restano editabili. In caso di rete assente resta visibile l'ultimo valore.
L'installer esegue lo script PostgreSQL direttamente tramite JDBC, verifica la presenza dei 3 trigger e il riallineamento iniziale, e registra: "Stock varianti: 3 trigger attivi verificati, totali prodotti riallineati." Se manca questo messaggio verificare di aver avviato il progetto aggiornato.
Corretto anche il primo inserimento multiplo di varianti: lo stock manuale precedente viene azzerato prima dell'inserimento, evitando di sommarlo alle varianti appena create.
Ulteriori file modificati: Product.java, ProductStockSchemaInitializer.java, db/product-stock.sql, product-view.html, product-form.html e docs/test-stock-varianti.sql. Aggiunto static/js/product-stock.js.
Verifiche eseguite: sintassi JavaScript e test automatici simulati su somma, stock nullo, movimento successivo, ultima variante eliminata, prodotto manuale, errore di rete e risposta non valida. Verificata inoltre la logica della query di aggregazione su un database SQLite temporaneo. Queste prove NON sostituiscono build Maven, mapping Hibernate e test PostgreSQL, non eseguibili in questo runtime.
Per installare in STS: fermare la vecchia applicazione, sostituire i sorgenti con questo progetto, eseguire mvnw.cmd clean test, poi riavviare come Spring Boot App. Non usare il vecchio JAR nella cartella target. Dopo l'avvio verificare il messaggio sopra e aprire la scheda prodotto; quantità varianti 3 e 5 devono dare 8, poi portando 3 a 1 il totale deve dare 6.
