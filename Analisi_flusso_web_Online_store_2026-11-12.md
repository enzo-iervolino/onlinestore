# Online-store — Analisi del flusso web

Data: 12 novembre 2026. Baseline: `online-store.zip` allegato in questa conversazione.
SHA-256 originale: `9d5317b20dcb427a7b8eab3a3b841943477516696febcc436e8f506b996cd0ca`.

Questa versione sostituisce le precedenti come riferimento per questa revisione. Non sono stati eliminati file storici dell'utente né modificati GitHub, VPS o database. La baseline originale resta intatta.

## Esito e perimetro

Il flusso contiene buone protezioni, ma non è ancora robusto nei casi di pagamento ripetuto, sessioni sovrapposte, variazioni del carrello e indisponibilità delle risorse dopo l'incasso. Sono presenti anche problemi di autorizzazione e di copertura del pagamento gift card da trattare con precedenza.

Questa consegna è una revisione con piano degli interventi, come richiesto: **nessuna correzione applicativa è stata applicata**. Classi, import, template, configurazioni e procedure admin sono invariati. Lo ZIP restituito contiene l'applicazione completa e questa analisi; sono esclusi soltanto `.git/` e `target/`, entrambi rigenerabili. Include anche immagini, video e uploads, per conservare il funzionamento del progetto.

Ho letto controller web, carrello e relativi modelli, selezione e processor dei pagamenti, integrazione PayPal, webhook e ritorno Stripe, snapshot, servizio e repository ordini, gestione stock, gift card, sconti, spedizioni, sicurezza, API prodotti, profilo ordini, template checkout effettivo, JavaScript e service worker. I file admin sono fuori dal perimetro di modifica; le API condivise sono state lette quando incidono sulla sicurezza pubblica.

**Limiti:** analisi statica dei sorgenti. Non sono stati eseguiti acquisti, chiamate con credenziali, test sul sito live, prove di penetrazione o accessi al database di produzione. Il tentativo `bash mvnw -o test -q` è bloccato dal parent Spring Boot 4.0.1 assente nella cache Maven. Non si può quindi attestare compilazione, avvio o superamento dei test; le annotazioni JPA indicano vincoli attesi, non provano lo schema effettivo in produzione. Le condizioni di concorrenza qui descritte sono dedotte dal codice e richiedono test concorrenti per essere riprodotte nell'ambiente reale.

## Aspetti già corretti da conservare

- I prezzi iniziali provengono dal database, non da un prezzo inviato dal browser; il problema successivo è il loro riutilizzo senza aggiornamento.
- Carrello base e varianti hanno chiavi distinte: positive per prodotti e negative per varianti.
- Stripe verifica la firma del webhook e rilegge dal provider lo stato `paid`.
- Il webhook Stripe non crea ordini vuoti quando manca lo snapshot.
- Gli snapshot persistono nel database, non soltanto nella sessione del browser.
- `Order.stripeSessionId` dichiara un vincolo univoco: è una protezione già presente, da completare con gestione dei conflitti.
- Lo stock viene decrementato con lock pessimista, impedendo che due scritture usino liberamente la stessa disponibilità.
- `GiftCard` possiede `@Version`: il consumo concorrente ha già una protezione ottimista. Il rischio residuo riguarda la gestione del conflitto dopo l'incasso, non la totale assenza di protezioni.
- Le API del profilo verificano la proprietà dell'ordine, compreso il PDF.
- Conferme email dopo commit: un errore SMTP non deve annullare l'ordine.
- PayPal ha già timeout di connessione e lettura.
- Il service worker attuale dismette le vecchie cache; non conserva pagine checkout offline.

## Interventi prioritari

P0 = rischio diretto per integrità del catalogo o copertura del pagamento. P1 = rischio elevato di incasso senza ordine, ordine incoerente o accesso improprio. P2 = affidabilità e chiarezza operativa. P3 = miglioramento strutturale.

### 1. P0 — API prodotti scrivibili senza autorizzazione

**Evidenza:** `security/SecurityConfig.java` concede `permitAll()` a `/api/products/**`, senza distinguere i metodi HTTP. `api/ProductRestController.create()` espone POST `/api/products`; `api/ApiProductRestController.deleteProduct()` espone DELETE `/api/products/{id}`. Non risultano annotazioni di autorizzazione sui due metodi né un'altra SecurityFilterChain nel codice esaminato.

**Conseguenza:** secondo queste regole applicative, un visitatore anonimo può invocare operazioni di creazione o eliminazione del catalogo. Questo incide direttamente su shop, prezzi, stock e checkout. Non è stata provata l'esposizione della VPS, che può avere altre protezioni esterne.

**Intervento:** separare letture pubbliche e scritture protette per metodo e percorso; richiedere il ruolo appropriato per tutte le scritture e preservare le chiamate lecite dell'admin. Nessuna modifica alla procedura admin è inclusa in questa consegna.

**Verifica:** anonimo e cliente ordinario non possono creare/eliminare; amministratore autorizzato continua a operare; GET pubbliche necessarie allo shop restano disponibili.

### 2. P0 — Gift card parziale accettabile come pagamento completo

**Evidenza:** `CheckoutController.pay()` accetta il metodo salvato in sessione. `GiftCardPaymentProcessor.start()` verifica soltanto codice e importo positivo, poi crea un ordine «PAGATO CON GIFTCARD». Non impone residuo da pagare uguale a zero né uguaglianza tra credito consumato e totale dovuto. Il template nasconde l'opzione quando il residuo è positivo, ma questo non protegge gli endpoint.

**Conseguenza:** una richiesta diretta può selezionare GIFTCARD con copertura parziale e far creare un ordine marcato pagato senza incassare la differenza. Scenario logico: dovuto 100 €, gift card 10 €, residuo 90 €; il processor non rifiuta questa combinazione.

**Intervento:** verificare sul server l'identità `totale dovuto = gift card + incasso esterno`; per gift card pura il residuo deve essere esattamente zero. Le gratuità da sconto integrale richiedono un percorso distinto e verificato: oggi il codice forza GIFTCARD anche quando il totale zero non deriva da una gift card.

**Verifica:** copertura parziale rifiutata per metodo GIFTCARD, totale coperto accettato una sola volta, totale zero senza gift card gestito secondo una regola esplicita.

### 3. P1 — Pagamento abbinabile al checkout sbagliato

**Evidenza:** `StripeCheckoutController.success()` e `PayPalCheckoutController.success()` ripiegano sul generico `CHECKOUT_TEMP_DATA` della sessione se non trovano lo snapshot. Il dato non viene confrontato obbligatoriamente con l'ID del pagamento restituito. Per Stripe il processor non imposta nello stesso modo di PayPal l'ID sul dato temporaneo prima del salvataggio.

**Conseguenza:** con due schede o snapshot scaduto, il pagamento A può essere associato ai dati di B. È un problema di associazione e integrità anche se il provider conferma che un pagamento è realmente avvenuto.

**Intervento:** usare esclusivamente una richiesta checkout persistente e immutabile associata a provider, ID pagamento, cliente/sessione e importi attesi; eliminare il fallback non vincolato. Senza associazione valida mostrare «pagamento in verifica» e riconciliare, senza creare un ordine dal carrello corrente.

**Verifica:** due schede con carrelli differenti, sessione nuova e snapshot assente non possono scambiare righe, cliente o indirizzo.

### 4. P1 — Incasso verificato senza confronto con importo e valuta attesi

**Evidenza:** i ritorni e webhook impostano `data.totalAmount` usando quanto riferito dal provider, senza confronto obbligatorio con il totale atteso salvato. `PayPalApiService` legge la valuta, ma il controller non la verifica; il parser restituisce importo della prima capture senza esporne e controllarne singolarmente lo stato. Stripe non confronta esplicitamente valuta e totale dello snapshot.

**Intervento:** rifiutare la finalizzazione automatica in caso di differenza tra valuta/importo attesi e confermati, o capture non completata; registrare capture ID/payment intent e relazione con checkout. Non correggere i numeri dell'ordine sostituendoli silenziosamente con quelli del provider.

**Verifica:** importo errato, valuta errata, capture in attesa e pagamento di altro checkout non producono ordini evadibili.

### 5. P1 — PayPal incassa prima di verificare i dati locali

**Evidenza:** nel ritorno PayPal `captureOrder()` precede il caricamento dello snapshot. La transazione database non può annullare una capture esterna.

**Conseguenza:** lo snapshot può essere assente o inutilizzabile dopo che il pagamento è già stato incassato.

**Intervento:** preparare e verificare il checkout persistente prima della capture; salvare uno stato durevole del tentativo; dopo timeout o errore locale interrogare PayPal e completare o avviare una gestione dell'eccezione. Non chiedere un nuovo pagamento senza prima verificare l'esito precedente.

### 6. P1 — PayPal dipende dal ritorno del browser

**Evidenza:** nel flusso shop esaminato, capture e creazione ordine sono nel GET `/paypal/success`; non è presente un webhook PayPal dedicato al completamento degli ordini shop.

**Conseguenza:** se il cliente approva ma chiude la pagina prima del ritorno, il normale completamento non parte; se la capture è avvenuta ma la risposta o il salvataggio falliscono, serve recupero indipendente dal browser.

**Intervento:** webhook con verifica di autenticità e riconciliazione periodica. Gestire approvazione, capture completata/in attesa/fallita e rimborsi; il webhook deve richiamare la stessa finalizzazione idempotente del ritorno, non duplicarne la logica.

### 7. P1 — Stock controllato troppo tardi rispetto all'incasso

**Evidenza:** `OrderServiceImpl.createOrderPaidBy...()` salva l'ordine e chiama `StockServiceImpl.decrementStockForPaidOrder()` dopo il pagamento esterno. Il lock evita scritture scorrette ma non riserva merce durante il pagamento.

**Conseguenza:** due clienti possono pagare l'ultimo pezzo; il secondo può restare con un incasso effettuato e ordine non finalizzato.

**Intervento:** prenotazione atomica con scadenza prima dell'incasso, rilascio per tentativo concluso senza pagamento e conversione a vendita al completamento. Se si sceglie di non prenotare, serve almeno un percorso durevole per pagamento ricevuto e merce indisponibile, con rimborso/gestione esplicita. Acquisire lock in ordine stabile per ridurre deadlock su carrelli multiarticolo.

### 8. P1 — Idempotenza incompleta e doppio clic

**Evidenza:** i controller eseguono «cerca ordine, poi crea», operazioni che possono correre in parallelo. Stripe ha il vincolo univoco, PayPal `Order.sessionId` non lo dichiara. La gift card pura non ha un identificativo univoco del tentativo. I processor possono creare più sessioni esterne per lo stesso acquisto; non risultano chiavi idempotenti nelle richieste esaminate.

**Conseguenza:** Stripe può generare un conflitto database da gestire; PayPal e gift card possono produrre più ordini per richieste simultanee, se le condizioni di stock e credito lo consentono. Più sessioni esterne distinte possono inoltre essere pagate realmente.

**Intervento:** tentativo checkout univoco, un solo pagamento attivo per quella revisione, chiavi idempotenti persistenti verso i provider, vincoli database per provider/riferimento, finalizzazione atomica unica. In caso di conflitto rileggere l'ordine già creato dopo rollback, senza ripetere scarico o consumo.

**Verifica:** due ritorni simultanei, webhook e ritorno simultanei, ripetizione dello stesso evento e doppio invio gift card devono produrre un solo ordine e un solo addebito locale.

### 9. P1 — Gift card nei pagamenti misti non riservata

**Evidenza:** il credito viene applicato al preventivo in sessione, ma consumato soltanto dopo Stripe/PayPal. `@Version` impedisce aggiornamenti concorrenti silenziosi, non garantisce che il saldo resti disponibile durante il pagamento esterno.

**Intervento:** riservare l'importo con scadenza e vincolo sul tentativo; riconvalidare stato, scadenza e importo al consumo; gestire il conflitto ottimista senza perdere traccia dell'incasso esterno. `consumeGiftCard()` deve anche rifiutare importi nulli/non positivi e normalizzare i centesimi.

### 10. P1 — Snapshot cancellato troppo presto

**Evidenza:** `CheckoutSnapshotService` imposta scadenza dopo due ore e in lettura cancella quelli scaduti. Dopo ordine completato lo snapshot viene eliminato. Stripe non configura nel processor una scadenza coordinata esplicitamente con queste due ore.

**Conseguenza:** pagamenti tardivi e webhook ritentati possono non avere più i dati necessari; perdere il preventivo originario limita anche la riconciliazione.

**Intervento:** distinguere scadenza del preventivo, prenotazione e conservazione della prova del tentativo. Non eliminare i dati di un pagamento non riconciliato; conservare lo storico minimo e applicare una politica di pulizia agli stati terminali dopo il periodo definito.

### 11. P1 — Prezzi e vendibilità non aggiornati al checkout

**Evidenza:** `CartItem` congela il prezzo all'aggiunta; `CheckoutController` lo copia nell'ordine senza rileggere il catalogo. L'aggiunta diretta per variantId non verifica `enabled`, mentre `/add-by-code` lo controlla. È possibile richiedere direttamente un prodotto base anche quando la vendita dovrebbe passare dalle varianti.

**Intervento:** prima di congelare il preventivo rileggere prodotti, varianti, appartenenza, disponibilità e prezzi; imporre su ogni endpoint le stesse regole di vendibilità. In caso di modifica mostrare il nuovo riepilogo e chiedere di proseguire con il totale aggiornato. Una volta iniziato il pagamento mantenere quel preventivo immutabile.

### 12. P1 — Sconto memorizzato in euro dopo modifiche del carrello

**Evidenza:** `applyCoupon()` salva importo e percentuale in sessione; `calculateTotals()` riusa l'importo, non ricalcola la percentuale sul nuovo subtotale e non rivalida il codice. L'identità del coupon non viene conservata nelle costanti checkout esaminate.

**Conseguenza:** applicare 10% a 1.000 € salva 100 €; ridurre il carrello a 100 € lascia potenzialmente 100 € di sconto. Anche il caso inverso dà un riepilogo incoerente. Un codice revocato o scaduto può restare applicato in sessione.

**Intervento:** conservare l'identificativo del coupon, rivalidarlo e ricalcolarlo prima dell'avvio del pagamento; imporre limiti 0–100% e sconto non superiore alla base ammessa. Congelare il risultato nel preventivo associato al tentativo.

### 13. P1 — CSRF escluso su tutto carrello e checkout

**Evidenza:** `SecurityConfig` esclude `/cart/**`, `/checkout/**`, `/paypal/**` e `/api/**` dalla verifica CSRF. La presenza degli hidden field nel template non compensa questa esclusione. `cart.js` non invia attualmente il token nelle sue chiamate.

**Intervento:** proteggere le mutazioni originate dal browser con token CSRF e aggiornare insieme JavaScript e form; esentare soltanto endpoint server-to-server autenticati con firma/verifica propria. Collaudare anche la semantica del `forward:/checkout/paga`. SameSite e CORS non sostituiscono l'autorizzazione delle API.

### 14. P1 — Dati cliente e spedizione validati soltanto nel browser

**Evidenza:** il form Java contiene stringhe e `normalize()`, senza validazione dei campi; `pay()` è invocabile direttamente e può costruire dati senza spedizione. Per utenti registrati i dati sono presi dal profilo e il form è bloccato anche se il profilo è incompleto.

**Intervento:** validazione server di nome, email, indirizzo, CAP, città, provincia, telefono e limiti di lunghezza; controllo prima di qualunque tentativo esterno, compresi utenti registrati. Nessun pagamento per un checkout incompleto. Eventuali variazioni dell'indirizzo devono seguire una regola chiara e finire nel preventivo.

### 15. P1 — Carrello condiviso tra richieste e più schede

**Evidenza:** `Cart` usa `LinkedHashMap` mutabile in HttpSession senza coordinamento; quantità modificate con somma/incremento di int. `CheckoutSession.clearAll()` cancella indiscriminatamente tutto il carrello quando ritorna un pagamento, anche se nel frattempo il cliente lo ha modificato.

**Intervento:** revisionare il carrello, coordinare le mutazioni per sessione oppure usare persistenza con versione; congelare la revisione pagata e rimuovere soltanto gli elementi relativi a quella revisione. Non cancellare un nuovo acquisto iniziato in un'altra scheda.

### 16. P1 — Stato locale dopo eccezioni non sempre dimostrabilmente atomico

**Evidenza:** il webhook ha una transazione sul controller e cattura eccezioni al suo interno, restituendo ResponseEntity. Le eccezioni uscite dai service transazionali possono marcare rollback-only, ma una eccezione catturata nel controller non provoca automaticamente rollback per il solo fatto di aver restituito HTTP 500. I ritorni effettuano chiamate esterne dentro la transazione database.

**Intervento:** un servizio unico delimita la transazione di finalizzazione; le eccezioni escono dal confine transazionale e vengono tradotte in risposta HTTP all'esterno. Tenere le chiamate remote fuori dai lock lunghi e registrare esiti recuperabili. Provare failure injection dopo ogni passo: ordine, stock, gift card, notifica, snapshot. Non presumere che un rollback DB annulli un pagamento esterno.

### 17. P2 — Conferma ordine pubblica con ID numerico

**Evidenza:** `/checkout/success?orderId=...` carica qualsiasi ordine senza controllare proprietario o sessione. **Il template attuale mostra solo il messaggio generico di successo e il link allo shop:** non è dimostrata, da questa pagina, una divulgazione attuale di indirizzi o righe. Restano conferma di esistenza e autorizzazione mancante, che diventerebbe più grave arricchendo il template.

**Intervento:** per utenti autenticati verificare proprietà; per ospiti usare token casuale limitato all'ordine/sessione. Risposta neutra per ordini non accessibili. Mostrare numero ordine, riepilogo e stato solo dopo autorizzazione. Mantenere le protezioni già presenti in `ProfileOrdersRestController`.

## Coerenza economica e completamento funzionale

### 18. P2 — Totale ordine e importo incassato confusi

`Order.totalAmount` è documentato come totale dell'ordine, ma viene popolato con il residuo incassato dal gateway; `netSubtotal` nel checkout include già la spedizione. Le righe `OrderItem.unitPrice` sono Double mentre altri importi sono BigDecimal. Inoltre il profilo riconosce GIFTCARD prima di PAYPAL e può etichettare un pagamento misto PayPal come sola gift card.

Definire e rendere coerenti: subtotale merce, sconto, spedizione, totale dovuto, quota gift card, quota esterna, valuta. Esempio: 120 € merce − 20 € sconto + 5 € spedizione = 105 € dovuti; 30 € gift card + 75 € Stripe/PayPal. Usare i medesimi significati in ordine, riepilogo, storico ed email/PDF. Per cambiare significati/campi condivisi con admin servono migrazione e regressione dedicate, senza cambiare la sua procedura operativa. Preferire importi decimali o centesimi interi in tutta la catena e controllare overflow nella conversione al provider.

### 19. P2 — Calcolo proposta gift card e importo applicato obsoleti

`recalcGiftProposal()` parte da `finalTotal`, che può già sottrarre la gift card applicata; inoltre le mutazioni del carrello non aggiornano centralmente la gift card. La proposta deve essere calcolata sul dovuto **prima** del credito, limitata al saldo disponibile/prenotabile e con due decimali; aggiornare la quota applicata quando cambia la base, senza consumare credito non necessario.

### 20. P2 — Spedizione gratuita per assenza di tariffa

`ShippingServiceImpl.calculateShipping()` restituisce zero quando non trova una fascia. Questo può essere voluto, ma un buco di configurazione diventa indistinguibile da spedizione gratuita. Richiedere una regola esplicita: nessuna tariffa applicabile deve bloccare il checkout oppure corrispondere a una gratuità deliberata. La base attuale è il subtotale prima dello sconto: mantenerla o cambiarla soltanto dopo aver stabilito la regola commerciale. Provare soglie, estremi, fascia mancante e totale zero. Non modificare il CRUD admin delle fasce in questa revisione.

### 21. P2 — Contanti proposti ma non implementati

`CashPaymentProcessor` restituisce «step 4 da implementare» e torna al checkout senza ordine. Nascondere/disabilitare il metodo nel web finché incompleto oppure implementare un ordine in attesa secondo una regola definita per ritiro/contrassegno. Non marcarlo pagato e non chiedere dati di pagamento elettronico per questo percorso.

### 22. P2 — Metodo sconosciuto convertito automaticamente in Stripe

`PaymentMethod.fromSessionValue()` restituisce Stripe anche per valori non validi; `normalizeAndSavePaymentMethod()` quindi può rispondere OK a un valore sconosciuto. Il JavaScript del checkout controlla solo `res.ok`, non il contenuto «OK/ERROR», e `pmSavedOk` non determina l'abilitazione finale del pulsante. Rifiutare esplicitamente valori non ammessi, usare risposta strutturata e confermare in interfaccia soltanto la scelta accettata. La decisione finale resta sul server, non nel JavaScript.

### 23. P2 — Quantità senza limiti e overflow

Gli endpoint applicano `Math.max(qty, 1)` ma non un massimo; aggiunte successive e `increase()` sommano int senza controllo. Imporre limiti per riga, righe e totale, quantità compatibili con la disponibilità e gestione esplicita di valori negativi o eccessivi. Non trasformare automaticamente qualunque quantità errata in 1. Il server deve verificare nuovamente le quantità anche prima del pagamento.

### 24. P2 — Pagina annullamento checkout mancante

`CheckoutController.cancel()` restituisce `checkout/checkout-cancel`, ma il template non è presente nello ZIP. Stripe e PayPal hanno ritorni di annullamento separati che rimandano al checkout. Uniformare il percorso mantenendo gli endpoint esistenti o aggiungere il template necessario. Annullare il browser non è prova che un pagamento sia fallito: interrogare lo stato prima di consentire ripetizioni incompatibili.

### 25. P2 — Conferme email senza coda persistente

`OrderNotificationService` avvia un thread daemon per email. La separazione dal commit è buona, ma un riavvio può perdere l'invio; gli errori sono soltanto loggati. Aggiungere invio persistente con retry, controllo duplicati e pool limitato. Generare il PDF dai dati salvati e ricaricati in modo sicuro, senza dipendere da relazioni lazy nell'oggetto passato al thread. Verificare allegato anche con open-in-view=false.

### 26. P2 — Codici gift card e tentativi ripetuti

`generateUniqueCode()` usa importo, timestamp e un suffisso casuale di due cifre. La porzione casuale è ridotta rispetto a un token di pagamento. Passare a codici con entropia crittografica adeguata e vincolo univoco; mantenere spendibili i vecchi codici con controllo compatibile. Limitare tentativi di ricerca/applicazione per sessione/IP e non registrare il codice completo nei log. Prevedere risposta e tempi che non facilitino enumerazione.

### 27. P2 — Errori tecnici e stati al cliente

I ritorni espongono in più punti `e.getMessage()`; per PayPal può includere frammenti delle risposte del provider. Usare messaggi semplici con riferimento di assistenza; log tecnici protetti e minimizzati. Distinguere pagamento fallito, annullato, ancora in verifica e pagato con completamento locale da recuperare. Pagamento incassato senza ordine non deve trasformarsi nel generico invito a ripagare.

## Conflitti e completamenti strutturali

### 28. P2 — Due controller su API prodotti sovrapposte

`ProductRestController` e `ApiProductRestController` sono entrambi sotto `/api/products`, con GET sulla radice e su `/{id}`. Il secondo specifica `produces=application/json`, il primo no: le condizioni non sono testualmente identiche, perciò non è corretto affermare senza avvio che l'applicazione fallisca certamente per mapping ambiguo. Rimane una sovrapposizione che può selezionare DTO differenti secondo negoziazione e richieste.

Stabilire un solo contratto per ciascuna rotta pubblica e verificare `Accept: application/json`, `*/*` e richieste browser. Prima di intervenire verificare le chiamate admin; non spostare o eliminare endpoint funzionanti senza una strategia compatibile. Esiste già anche `web/admin/AdminProductRestController` sotto `/api/admin/products`: evitare di creare una quarta soluzione parallela.

### 29. P2 — Confine ruoli da verificare separatamente

La configurazione impone esplicitamente ADMIN alle sole rotte delle spese di spedizione; il resto ricade in autenticazione generica, salvo singole altre protezioni esterne. Per il presente audit questo è un rilievo sul confine pubblico/riservato, non una revisione delle procedure admin. Serve un controllo dedicato che un normale cliente autenticato non raggiunga operazioni gestionali. Non sono state modificate quelle procedure né attestata la loro sicurezza complessiva.

### 30. P2 — Sessione, sicurezza del trasporto e ambiente reale

Verificare in produzione HTTPS, attributi Secure/HttpOnly/SameSite del cookie, proxy fidati e intestazioni inoltrate, assenza di cache di pagine personali, esposizione dei soli upload previsti, credenziali esterne ai sorgenti e visibilità delle eccezioni. Le properties usano variabili d'ambiente per molte impostazioni: dal solo ZIP non si può certificare il valore attivo sulla VPS. Il service worker attuale è già di dismissione e non richiede la ricreazione di una PWA per risolvere questi punti.

### 31. P2 — Rimborsi, contestazioni e riconciliazione

Nella catena shop esaminata non c'è un ciclo completo automatico per rimborso, contestazione, storno gift card e riallineamento stock. Definire stati e transizioni con storico: preparato, pagamento avviato, pagato, completamento da recuperare, fallito, annullato, rimborsato/parzialmente rimborsato. La gestione della spedizione resta distinta dal pagamento. Il recupero deve evitare doppi rimborsi o ripristini stock. Per la fase iniziale basta anche un processo operativo tracciato, purché i casi eccezionali siano rilevati e non persi.

### 32. P3 — Prestazioni, dipendenze e prove mancanti

Il catalogo restituisce liste senza paginazione in più punti: introdurre limiti coerenti con la dimensione reale. Aggiungere rate limit ai punti costosi, checkout e verifiche codici. Verificare versioni supportate e vulnerabilità delle dipendenze con uno scanner aggiornato; questo audit non dichiara CVE specifiche senza una verifica dedicata. I test presenti riguardano avvio contesto, spedizioni e codici prodotto: non costituiscono copertura della catena pagamenti e concorrenza.

## Sequenza consigliata degli interventi

1. Chiudere scritture pubbliche del catalogo e falla gift card parziale; riattivare CSRF coordinando i client. Testare i permessi senza cambiare l'operatività admin.
2. Centralizzare il preventivo verificato: catalogo, quantità, coupon, spedizione, credito, dati cliente; salvare una revisione immutabile e un identificativo univoco.
3. Unificare la finalizzazione transazionale e idempotente richiamata da ritorni e webhook; eliminare il fallback al checkout non correlato; confrontare importi e valuta.
4. Introdurre prenotazioni/compensazioni per stock e credito; vincoli e migrazioni database verificate. Conservare dati fino a riconciliazione conclusa.
5. Completare PayPal senza dipendenza dal browser e gestione dei timeout; recupero automatico dei pagamenti sospesi.
6. Allineare significato dei totali, conferma autorizzata, email persistenti, messaggi e metodi incompleti; poi collaudo completo e regressione admin in sola verifica.

Non basta aggiungere controlli separati nei quattro processor: prezzi, credito, stock e finalizzazione devono seguire le stesse regole, altrimenti i conflitti si spostano da un percorso all'altro. Il miglioramento può riusare controller, service e repository già presenti, aggiungendo soltanto la persistenza realmente necessaria ai tentativi/prenotazioni; evitare classi duplicate e riscritture del gestionale.

## Collaudo richiesto prima di dichiarare il flusso affidabile

| Caso | Risultato atteso |
|---|---|
| Ospite e utente registrato, Stripe e PayPal | Un ordine, dati completi, totale corretto, conferma accessibile soltanto al cliente |
| Carta rifiutata o pagamento annullato | Nessun ordine marcato pagato, prenotazioni rilasciate secondo stato verificato |
| Gift card totale e parziale | Totale coperto una volta; differenza sempre incassata quando dovuta |
| Totale zero senza gift card | Percorso esplicito e valido, senza credito inventato |
| Coupon seguito da modifica quantità/prezzi | Sconto ricalcolato e nuovo totale confermato prima di pagare |
| Coupon scaduto/disabilitato e gift card scaduta | Nessun vantaggio applicato a nuovi tentativi non validi |
| Variante disabilitata/ID base richiesto direttamente | Vendita rifiutata se non conforme alle regole del catalogo |
| Due clienti sull'ultimo pezzo | Nessun secondo incasso non gestito; prenotazione o compensazione tracciata |
| Due acquisti sullo stesso credito | Nessun consumo doppio o credito negativo; eventuale incasso esterno sempre recuperabile |
| Doppio clic e due richieste simultanee | Un solo tentativo attivo e un solo ordine per acquisto |
| Webhook Stripe e ritorno simultanei | Una sola finalizzazione, una sola email programmata, un solo scarico |
| Ripetizione eventi e callback | Risposta coerente senza ripetere effetti |
| Importo/valuta/pagamento di altro checkout | Ordine non finalizzato automaticamente; anomalia registrata |
| Snapshot scaduto, sessione persa, browser chiuso | Pagamento riconciliabile senza invito improprio a pagare di nuovo |
| Due schede e nuovo carrello durante il pagamento | Nessuno scambio di indirizzo/righe, nessuna cancellazione del nuovo carrello |
| Timeout dopo capture PayPal | Interrogazione del provider, nessun nuovo addebito alla cieca |
| Eccezione dopo ogni passo locale | Atomicità locale verificata e incasso esterno conservato come evento recuperabile |
| Riavvio dopo ordine ma prima dell'email | Notifica ripresa da coda persistente senza duplicazioni |
| GET conferma con ID di altro cliente | Nessun accesso e nessuna divulgazione |
| POST senza CSRF e API scrittura anonime | Rifiuto; webhook autentici continuano a funzionare |
| Richieste catalogo con diversi Accept | Risposta prevedibile con DTO e rotta definiti |
| Tariffe ai confini e configurazione incompleta | Nessuna gratuità accidentale o costo duplicato |
| Ordine misto e PDF | Totale dovuto distinto da credito e incasso gateway, somma coerente |
| Admin esistente | Nessuna regressione di endpoint, procedure o template |

## Riferimenti tecnici primari consultati

Questi riferimenti sostengono le raccomandazioni generali; le evidenze sul progetto provengono dai file elencati sopra.

- Stripe, fulfillment: https://docs.stripe.com/checkout/fulfillment — webhook, verifica lato server, finalizzazione unica anche con chiamate concorrenti.
- PayPal, richieste REST: https://developer.paypal.com/api/make-api-requests — header PayPal-Request-Id e idempotenza delle richieste supportate.
- PayPal, webhooks: https://developer.paypal.com/api/rest/webhooks — notifiche e verifica autenticità.
- Spring Security, CSRF: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html — protezione delle mutazioni provenienti dal browser e integrazione token.

Conclusione operativa: mantenere l'impianto esistente, correggere prima copertura economica e autorizzazioni, poi rendere pagamento e ordine un processo persistente, correlato e recuperabile. Lo ZIP allegato documenta lo stato analizzato; non deve essere interpretato come una versione già corretta o certificata per la produzione.
