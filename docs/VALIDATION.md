# Validation et limites

## État vérifié
Le moteur géométrique FOLLOW est accompagné de tests automatisés. Cela ne signifie pas que le contrôle du pointeur physique Android fonctionne.

## Limites Android à valider sur matériel
Pointer Capture demande qu'une fenêtre de l'application ait le focus. Une application ordinaire ne peut pas intercepter en permanence tous les événements de la souris système, ni injecter librement ses propres événements dans d'autres applications. LOCAL → DISTANT doit donc utiliser une action explicite et une fenêtre capturant le pointeur ; DISTANT → DISTANT peut alors suivre la géométrie. Un service d'accessibilité exige un consentement explicite et ne garantit pas la capture globale de souris. Un IME personnalisé reçoit du texte sous ses propres conditions de sélection.

## Avant toute V1
Compiler de vrais APK et un exécutable Windows ; valider protocole chiffré et appairage ; tester 2 puis 3 appareils et quatre directions avec souris physique ; vérifier clavier physique, dictée, interruption, reconnexion, consentement, service et consommation ; publier les binaires réellement produits et vérifier leurs liens.

Aucun de ces essais matériels n'est affirmé réalisé.
