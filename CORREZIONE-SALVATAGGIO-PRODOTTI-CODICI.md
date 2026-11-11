# Correzione salvataggio prodotti e codici articolo

## Problema corretto

La gestione dei codici alternativi era stata collegata direttamente a `ProductService.save()`.
Se la tabella `product_codes` non era ancora disponibile oppure la validazione dei codici falliva,
il prodotto non arrivava al normale salvataggio.

## Flusso attuale

1. Il prodotto viene validato e salvato con il flusso originale.
2. Il salvataggio del prodotto non dipende dalla tabella dei codici alternativi.
3. I codici vengono salvati successivamente in una transazione indipendente.
4. Se i codici presentano un errore, il prodotto resta salvato e la pagina mostra il motivo reale.
5. La tabella `product_codes` viene verificata all'avvio senza impedire l'avvio o il salvataggio dei prodotti in caso di problema DDL.
6. Ogni codice viene inserito come riga distinta e viene riletto dal database per verificare che il salvataggio sia completo.

## Esempio di prova

Usare un codice interno diverso dai codici alternativi.

Codice interno prodotto:

```text
INT-TEST-101
```

Codici alternativi:

```text
EAN13:8001234567897
BARCODE:BC-TEST-101
FORNITORE:SUP-TEST-101
ALTRO:ALT-TEST-101
```

Non inserire `BARCODE:INT-TEST-101`, perché coinciderebbe con il codice interno della stessa scheda.
