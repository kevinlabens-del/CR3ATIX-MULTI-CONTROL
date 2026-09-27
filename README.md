# CR3@TIX MULTI CONTROL

**Statut : alpha installable pour essais Android ↔ Android et Android → Windows. V1 multi-appareils non validée.**

- [Page publique et téléchargements](https://kevinlabens-del.github.io/CR3ATIX-MULTI-CONTROL/)
- [Release alpha : APK et Receiver Windows](https://github.com/kevinlabens-del/CR3ATIX-MULTI-CONTROL/releases/tag/v0.1.0-alpha)
- [Installation et appairage](docs/INSTALLATION.md) · [limites et validation](docs/VALIDATION.md)

L'APK peut agir comme contrôleur ou Receiver Android. Le Receiver Windows est un programme visible. Le réseau local utilise TLS et une empreinte SHA-256 du certificat que l'utilisateur doit vérifier, puis un code temporaire. Le contrôle Android Receiver demande d'activer volontairement un service d'accessibilité et utilise des gestes tactiles ; il ne déplace pas le curseur système.

**Actuellement disponibles dans l'alpha :** connexion manuelle à une cible, mode LOCAL/DISTANT, touchpad, texte et touches simples, souris physique dans l'application via Pointer Capture, commande vocale à la demande, Receiver Android expérimental et Receiver Windows.

**Encore requis pour la V1 :** plusieurs connexions simultanées, assistant complet, vrai layout FOLLOW relié aux entrées, reconnexion, diagnostic, macros, fichiers, permissions et service complet, essais réels prolongés. Les tests automatiques du moteur FOLLOW ne remplacent pas les essais matériels.
