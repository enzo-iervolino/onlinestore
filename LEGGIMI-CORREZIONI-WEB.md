# Online-store — correzioni flusso web, 12 novembre 2026

Baseline: `online-store.zip` fornito dall'utente, SHA-256 `9d5317b20dcb427a7b8eab3a3b841943477516696febcc436e8f506b996cd0ca`.

Lo ZIP contiene l'intera applicazione con sorgenti e template completi, configurazioni, immagini, video, uploads e test. Sono esclusi solo `.git` e `target`; non è necessario sovrascrivere gli uploads del server con quelli della baseline per aggiornare il codice. Non sono state effettuate modifiche a GitHub, VPS o database live.

## Cosa è stato implementato

| Area | Comportamento nuovo |
|---|---|
| Autorizzazioni | Letture del catalogo pubbliche, scritture prodotti riservate ad ADMIN; clienti ordinari esclusi dalle rotte `/admin/**` e `/api/admin/**`. |
| CSRF | Riattivato su carrello e checkout; token inviato dalle chiamate fetch del sito e dai form. Restano le esenzioni API preesistenti, protette dai rispettivi permessi, e i webhook autenticati dal provider. |
| Carrello | Quantità limitate a 1–999 per riga e massimo 100 righe, controllo overflow, copie delle righe in lettura e coordinamento delle richieste della stessa sessione. |
| Prodotti/varianti | Varianti disattivate respinte; non si può eludere la scelta della variante acquistando direttamente il prodotto base. Prezzi riletti dal catalogo. |
| Riepilogo | Ogni conferma include l'impronta del riepilogo mostrato. Se prezzi, coupon, spedizione o quota gift cambiano si torna al riepilogo prima del pagamento. |
| Coupon | Conservazione del codice, rivalidazione e ricalcolo sul subtotale aggiornato; limiti percentuali e monetari. |
| Spedizione | Controllo server di nome, email, telefono, indirizzo, CAP, città e sigla provincia; resta la spedizione italiana già prevista. Un profilo incompleto deve essere completato prima di pagare. |
| Gift card | Il metodo puro richiede residuo esattamente zero; quota parziale richiede Stripe/PayPal. Importi con due decimali, credito prenotato sotto lock, nuovi codici casuali robusti; vecchi codici ancora riconosciuti. |
| Ordini a zero | Il checkout può completare un totale zero validato anche senza gift card; stato dedicato quando non vi è credito utilizzato. |
| Tentativo persistente | Nuova entità `CheckoutAttempt`, distinta dagli ordini definitivi, con preventivo congelato, proprietario, metodo, riferimento gateway e stato. Una sessione può avere un solo tentativo attivo. |
| Prenotazione | Stock e credito spendibile vengono sottratti atomicamente all'avvio e non nuovamente al saldo. Al rilascio verificato vengono restituiti una volta sola. |
| Stripe | Chiave idempotente per creazione sessione, riferimento checkout nei metadata, scadenza sessione, conferma comune per webhook/ritorno/recupero e confronto importo/valuta. |
| PayPal | Chiavi idempotenti per creazione/capture; verifica del riepilogo e dell'importo/valuta dell'ordine PRIMA della capture; verifica capture completata e importo/valuta DOPO; gestione risposta già acquisita. |
| Recupero | Job periodico interroga Stripe/PayPal anche senza ritorno del browser, completa le approvazioni PayPal valide e recupera il pagamento solo gift card interrotto. Gli errori non invitano a pagare di nuovo. |
| Webhook PayPal | Endpoint `/paypal/webhook`, autenticità verificata tramite API PayPal prima di elaborare; facoltativo se si usa il recupero periodico. |
| Concorrenza | Lock sul tentativo durante la finalizzazione. Ordine, credito, notifica e stato definitivo vengono salvati nella stessa transazione. Ritorni/eventi ripetuti non ripetono gli effetti. |
| Snapshot precedenti | Nessun ripiego sui dati generici di un altro checkout in sessione. Pagamenti vecchi possono essere completati dal loro snapshot DB, caricato sotto lock; la sola scadenza di due ore non lo cancella più. |
| Schede diverse | Il ritorno di un acquisto non cancella il carrello di una revisione o di un nuovo tentativo differente, anche con articoli identici. |
| Privacy | Conferma ordine autorizzata per proprietario del tentativo o utente intestatario. Per gli ospiti la sessione conserva un token casuale; l'ID numerico da solo non basta. |
| Stato pagamento | Nuova pagina `/checkout/in-attesa` con riepilogo congelato, riferimento, ripresa dello stesso pagamento, verifica e annullamento verificato. |
| Email | Coda DB persistente, worker limitato e retry; niente thread daemon per ordine. PDF creato ricaricando l'ordine in transazione. |
| Totali | Subtotale netto merce separato dalla spedizione; documenti cliente distinguono totale ordine, gift card e quota altro pagamento. Il campo legacy `totalAmount` mantiene la quota esterna già usata dal flusso, senza migrazioni invasive dei vecchi ordini. |
| Metodi e percorsi | Metodo sconosciuto rifiutato; contanti non proposti nel web finché non implementati, senza cambiare il gestionale. Aggiunta pagina annullamento mancante. |
| API sovrapposte | Rimosse le annotazioni GET di fallback duplicate di `ProductRestController`; le rotte root/dettaglio esistenti restano servite da `ApiProductRestController`, con le sue risposte JSON. |
| Abusi | Limiti in memoria per avvii pagamento e verifica codici, per IP; messaggi tecnici riservati ai log. |

## Cosa resta invariato dell'admin

Controller e template sotto `web/admin`, `templates/admin` e gli altri controller amministrativi esistenti non sono stati riscritti o sostituiti. I relativi file sono confrontati con la baseline. Rimangono gli stessi endpoint e le stesse procedure; cambia il controllo d'accesso per escludere i normali clienti. Il modello `Order` è invariato.

Sono stati necessariamente modificati alcuni servizi condivisi: prenotazioni, invio email/PDF, selezione metodo, lettura dei dati e permessi. Le email non sono più inviate immediatamente nel thread della richiesta, ma dal worker dopo il commit. Verificare che l'account gestionale abbia `ROLE_ADMIN` prima di usare questa versione.

## Database e primo avvio

Sono aggiunte soltanto due tabelle:

- `web_checkout_attempts`: preventivi, riferimenti provider e stato delle prenotazioni.
- `web_order_mail_jobs`: invii conferma da elaborare/riprovare.

Con la configurazione preesistente `spring.jpa.hibernate.ddl-auto=update`, Hibernate crea le tabelle mancanti. Se l'installazione usa `validate` o `none`, eseguire prima lo script PostgreSQL `docs/migrations/2026-11-12-web-checkout.sql`, dopo backup e verifica sul database di prova. Lo script non viene eseguito automaticamente da questo pacchetto.

Nessun nuovo valore reale di credenziali è stato inserito. Restano Stripe, PayPal, posta e base URL configurati tramite le variabili esistenti.

### Nuove impostazioni

| Variabile | Default | Significato |
|---|---|---|
| `CHECKOUT_RECOVERY_DELAY_MS` | `60000` | Intervallo tra cicli di recupero; il lavoro effettivo dipende anche dai tempi di risposta dei provider. |
| `PAYPAL_WEBHOOK_ID` | vuoto | ID dell'endpoint registrato su PayPal, necessario solo per accettare il webhook autenticato. |
| `CHECKOUT_REQUIRE_SHIPPING_RATE` | `false` | Mantiene la compatibilità: senza fascia si usa zero. Impostare `true` per bloccare i checkout senza tariffa, dopo aver completato le fasce. |
| `SESSION_COOKIE_SECURE` | `true` nel profilo prod | Cookie solo HTTPS in produzione. In HTTP locale usare il profilo locale o configurare il valore coerentemente. |

Per il webhook PayPal registrare `https://DOMINIO/paypal/webhook` sul conto/app corretti e gli eventi `CHECKOUT.ORDER.APPROVED`, `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.PENDING`. Senza ID il webhook rifiuta le richieste; il recupero periodico dei tentativi shop già associati continua a funzionare.

Mantenere il webhook Stripe esistente `/stripe/webhook`, includendo eventi completed, async_payment_succeeded ed expired. Il processor shop usa ancora il pagamento carta. Per entrambi i gateway lo shop richiede EUR.

## Regole delle prenotazioni e casi eccezionali

Le quantità e il saldo mostrati durante un pagamento sono le disponibilità **dopo le prenotazioni**. Un tentativo avviato non è ancora un ordine pagato. Non rettificare manualmente quelle quantità ignorando le prenotazioni pendenti.

Stripe riceve una sessione con scadenza a 35 minuti dalla preparazione. Per PayPal, un tentativo PENDING scaduto non viene acquisito dal recupero automatico; un tentativo già CAPTURING viene invece riconciliato perché può aver incassato. Non si rilasciano risorse su un semplice errore di rete.

Un avvio senza riferimento gateway, e quindi senza link di pagamento esposto dall'app, viene abbandonato dopo 40 minuti. Il bind rifiuta risposte tardive dopo l'annullamento. Per tentativi associati, il rilascio richiede uno stato terminale non pagato verificato; Stripe deve risultare expired. Il pulsante annulla non equivale a un rimborso.

`PAID_REVIEW` conserva i dati e la prenotazione quando un pagamento ricevuto non è finalizzabile in modo sicuro (ad esempio importi divergenti). Il job continua a verificare; consultare provider e log, non effettuare un nuovo addebito né rilasciare manualmente credito/stock senza riconciliazione. Il controllo dei tentativi ruota per ultima verifica, così un gruppo di anomalie vecchie non esclude indefinitamente i nuovi tentativi.

Rimborsi, contestazioni, annullamenti gestionali e rettifiche inventariali restano decisioni operative del negozio: non sono stati aggiunti rimborsi automatici né modificata la procedura admin. La versione registra e recupera il completamento degli acquisti, non inventa nuove regole commerciali per resi e rimborsi.

Gli snapshot precedenti, già cancellati dalla vecchia versione, non possono essere ricostruiti da questo aggiornamento. Prima del passaggio conviene completare/riconciliare gli acquisti già aperti nella versione precedente; il percorso legacy continua a usare il suo stock e credito al completamento, poiché non aveva prenotazioni. Le nuove prenotazioni proteggono i tentativi creati da questa versione.

Per ospiti, la pagina di conferma richiede la stessa sessione: dopo perdita del cookie o uso di altro dispositivo l'ID ordine non concede accesso. La conferma email/PDF resta il documento ricevuto dal cliente.

La coda email è persistente e impedisce l'accodamento ordinario duplicato. SMTP non garantisce esattamente una consegna: se il server si arresta dopo l'invio e prima del commit del job può essere necessario un retry, con possibile doppia email. Ordine, stock e gift card non vengono ripetuti per questo motivo.

Gli snapshot e i tentativi non vengono eliminati automaticamente prima della riconciliazione. Va definita separatamente una conservazione dei dati adeguata all'installazione; non è stata introdotta una cancellazione massiva dei dati degli utenti.

## Verifiche effettivamente eseguite

- Compilazione di tutti i sorgenti Java di produzione, tranne `Order.java` invariato e annotato Lombok, tramite compilatore JDK disponibile e librerie estratte dal JAR fornito. Usato il binario baseline della sola classe `Order`, senza modificarla. Sorgente e target 17; runtime di verifica Java 21. Compilazione riuscita, senza nuovi errori.
- **36 verifiche di regressione** sulle classi e servizi reali, con repository sostituiti da piccoli doppi in memoria: copertura totale/parziale, coerenza importi e centesimi, dati cliente, quantità, prenotazioni, consumi singoli, rilascio singolo, riferimento/valuta estranei, visibilità ospiti, carrelli di altre schede, recupero PayPal e solo gift card. Tutte superate.
- **10 verifiche HTTP** su Tomcat con la `SecurityConfig` reale, autenticazione di prova e controller minimi: letture anonime, scritture anonime negate, CSRF, esclusione cliente dal catalogo scrivibile e dall'area admin, accesso admin e checkout con token. Tutte superate.
- Hibernate: validazione offline dei mapping delle **26 entità** e parsing delle nuove query HQL; nessuna connessione al database utente.
- Sintassi JavaScript del client CSRF e checkout controllata con Node.
- Confronto dei controller/template admin e controllo d'integrità dello ZIP.

I sorgenti dei controlli supplementari e i risultati sono in `verifiche-web/`. La suite di regressione è anche collegata a JUnit da `CheckoutFlowRegressionTest`.

**Non sono attestati:** build Maven completa, avvio dell'intera applicazione con PostgreSQL, isolamento transazionale/concorrenza su DB reale, invio SMTP reale, chiamate reali Stripe/PayPal o collaudo browser dell'intero sito. Maven non raggiunge Maven Central nell'ambiente di lavoro; non è un errore di compilazione riscontrato nel codice. Le prove HTTP coprono il filtro di sicurezza, non l'intera applicazione con tutti i dati e controller.

## Collaudo da eseguire in STS prima della pubblicazione

1. Importare come progetto Maven la cartella `online-store` estratta dallo ZIP. Usare il database PostgreSQL di prova e credenziali sandbox; non un database produttivo copiato senza isolamento.
2. Con JDK 17 o superiore, eseguire `mvnw.cmd clean test` su Windows, poi `mvnw.cmd clean package` (PowerShell: `./mvnw.cmd ...`). Verificare che venga avviata la nuova cartella, non una vecchia copia STS.
3. Provare ospite e utente registrato, Stripe, PayPal, gift card totale/parziale e totale zero; confermare somme e PDF. Verificare aumento/riduzione quantità dopo coupon, variante disattivata e profilo incompleto.
4. Provare doppio invio, ritorno ripetuto e webhook insieme al ritorno, usando due richieste realmente concorrenti sul PostgreSQL di prova. Deve esistere un solo ordine per tentativo.
5. Due clienti devono contendersi l'ultimo pezzo senza entrambi avviare un pagamento prenotato. Ripetere con lo stesso saldo gift card.
6. Chiudere il browser dopo approvazione, riavviare l'app e verificare il recupero del tentativo, poi l'invio email. Provare SMTP indisponibile e riavvio prima dell'invio.
7. Controllare annullamento verificato, scadenza, timeout, due schede e nuovo carrello mentre il primo acquisto termina. Verificare che non venga cancellato il nuovo carrello.
8. Verificare che l'admin esistente possa ancora operare e che un normale cliente non raggiunga le operazioni gestionali.
9. Per la pubblicazione salvare backup del JAR e del database prima dell'aggiornamento. Non distribuire il vecchio `target` dello ZIP originale: questo pacchetto non include alcun JAR compilato.

Un eventuale rollback non consiste nel solo ritorno al vecchio JAR quando esistono prenotazioni attive: riconciliare prima tentativi e saldi oppure predisporre un ripristino coerente di applicazione e database. Conservare sempre i riferimenti dei pagamenti reali avvenuti dopo il backup.
