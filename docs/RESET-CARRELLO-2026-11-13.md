# Reset checkout quando il carrello viene svuotato

Baseline: online-store.zip caricato il 13/11/2026.

La rimozione completa, la rimozione dell’ultimo articolo e la riduzione a zero
azzerano coupon, percentuale/importo sconto, codice/importi gift card,
spedizione, metodo di pagamento e dati temporanei del checkout.
La pulizia avviene sia dalle API del mini-carrello sia dalla pagina carrello.
Un carrello assente o vuoto viene ripulito anche prima di riutilizzarlo.
Un carrello ancora popolato conserva le selezioni.
Il saldo reale della gift card non viene modificato da questa operazione.
I tentativi di pagamento pendenti e la loro titolarità restano gestiti dal
flusso esistente per consentire callback e riconciliazione sicuri.

Verifiche: compilazione delle tre classi modificate con target Java 17;
9 scenari CartResetChecks e 36 controlli CheckoutFlowRegressionChecks superati.
Maven non è disponibile nell’ambiente di verifica: build Maven completa da
eseguire in STS con `mvn clean test` e `mvn clean package`.
Nessuna migrazione DB necessaria.

Archivio completo con sorgenti, configurazioni e immagini uploads originali.
Esclusi target, .git, cache IDE e precedenti log di verifica.
