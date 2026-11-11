# Gestione unificata dei codici articolo

## Struttura adottata

Il progetto mantiene i campi già esistenti:

- `products.codpro`: codice interno principale del prodotto;
- `product_variants.variant_code`: codice interno principale della variante.

I codici aggiuntivi vengono salvati nella nuova tabella `product_codes` e possono appartenere:

- direttamente a un prodotto, quando il codice identifica l'articolo senza una variante specifica;
- a una variante, quando EAN/barcode deve identificare colore e taglia precisi.

Ogni codice alternativo viene salvato sia nel formato originale (`code_value`) sia normalizzato (`normalized_code`). Il valore normalizzato è univoco in tutta l'applicazione, evitando che lo stesso EAN o barcode venga assegnato a due articoli diversi.

## Tipi disponibili

- `INTERNAL`
- `EAN`
- `EAN13`
- `EAN8`
- `UPC`
- `BARCODE`
- `SUPPLIER`
- `OTHER`

Nei form amministrativi si inserisce un codice per riga:

```text
EAN13:8001234567890
BARCODE:123456789
FORNITORE:SUP-001
```

È possibile inserire anche il solo valore. In tal caso il tipo viene riconosciuto automaticamente quando il codice è numerico (EAN-13, EAN-8, UPC o barcode generico).

## Ordine di ricerca

La ricerca unificata esegue questi controlli:

1. codici alternativi configurati nella tabella `product_codes`;
2. codice interno della variante (`variant_code`);
3. codice interno del prodotto (`codpro`).

Un codice associato a una variante restituisce direttamente la variante corretta, compresi prezzo, stock, stato e immagine.

## Endpoint di lettura

### Individuazione articolo

```http
GET /api/products/lookup?code=8001234567890
```

Risposta: prodotto o variante individuata, codice corrispondente, prezzo, stock e identificativi.

### Inserimento nel carrello tramite scanner

```http
POST /cart/add-by-code?code=8001234567890&qty=1
```

Se il codice appartiene a una variante viene aggiunta la variante; altrimenti viene aggiunto il prodotto padre. Una variante disabilitata non viene inserita.

## Ricerca amministrativa

La ricerca prodotti considera ora anche:

- `codpro`;
- nome prodotto;
- codice variante;
- EAN, UPC, barcode e codici alternativi del prodotto o delle varianti.

## Database

Con `spring.jpa.hibernate.ddl-auto=update`, Hibernate crea automaticamente la tabella `product_codes` e i relativi indici al primo avvio della nuova versione.


## Correzione salvataggio multiplo

Il salvataggio dei codici viene ora eseguito in una singola transazione tramite SQL parametrizzato,
con verifica immediata del numero e dei valori realmente presenti nella tabella `product_codes`.
La tabella viene inizializzata in modo idempotente da `src/main/resources/schema.sql` prima dell'avvio JPA.
Dopo ogni salvataggio, l'interfaccia mostra il numero di codici registrati e i codici effettivamente letti dal database.
