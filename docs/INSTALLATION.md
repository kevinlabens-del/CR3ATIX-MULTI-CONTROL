# Essai technique Android → Windows ou Android

Ce build 0.1.0 est un **essai technique** ; il ne valide pas la V1 multi-appareils.

1. Dans GitHub Actions, télécharge les deux artifacts du workflow **Native builds** réussi. Les artifacts Actions nécessitent une connexion GitHub.
2. Sur Windows, lance `MultiControlReceiver.exe`. Windows Defender peut demander une autorisation réseau privé. N'autorise pas les réseaux publics. Pour Android ↔ Android, installe l'APK sur les deux appareils, touche **ACTIVER RECEIVER** sur l'appareil cible et active explicitement **AUTORISER GESTES ANDROID** dans les réglages d'accessibilité.
3. Note l'IP locale du PC (`ipconfig`) ou de l'Android Receiver (réglages Wi-Fi), le code à six chiffres et l'empreinte TLS affichés. Sur Windows, la clé et le certificat sont enregistrés dans `%LOCALAPPDATA%\CR3ATIX-MultiControl`.
4. Sur Android, installe l'APK debug, ouvre l'application, saisis les trois valeurs et touche **CONNECTER**. Compare l'empreinte caractère par caractère ; n'accepte jamais une empreinte inattendue.
5. Utilise le touchpad, le clavier et la dictée. **LOCAL ↔ DISTANT** revient au contrôle Android. Le Receiver Windows doit rester visible ; sur Android, sa notification de service reste visible. Les gestes Android nécessitent le service d'accessibilité activé.
6. Pour couper les connexions et changer de code, clique **TOUT DÉCONNECTER** sur Windows ou **ARRÊTER RECEIVER** sur Android.

Le code est nécessaire à chaque nouvelle connexion ; l'application conserve seulement l'adresse et l'empreinte du PC. Les événements clavier et souris ne sont reçus par l'application Android que lorsqu'elle a le focus et, pour les mouvements physiques, que Pointer Capture est actif. Aucun accès à distance Internet n'est configuré. Le texte envoyé passe temporairement par le presse-papiers Windows pour l'insertion Unicode : ne l'utilise pas pour des secrets.

Sur le Receiver Android, la commande de mouvement déplace le point du prochain geste tactile, sans déplacer le curseur système. Le clic droit et certaines touches n'ont pas d'équivalent système et peuvent être ignorés. Le collage exige un champ Android éditable déjà sélectionné ; il modifie le presse-papiers du Receiver. Le protocole chiffre la connexion et vérifie l'empreinte, mais la V1 n'a pas encore subi d'audit de sécurité ni de tests matériels multi-appareils.

**Non livré dans ce build :** plusieurs cibles simultanées, layout FOLLOW intégré à l'application, reconnexion automatique, transfert de fichiers, macros et release stable. Ces fonctions restent à développer et tester avant d'appeler ce projet V1.
