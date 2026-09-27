# Essai technique Android → Windows

Ce build 0.1.0 est un **essai technique** ; il ne valide pas la V1 multi-appareils.

1. Dans GitHub Actions, télécharge les deux artifacts du workflow **Native builds** réussi. Les artifacts Actions nécessitent une connexion GitHub.
2. Sur Windows, lance `MultiControlReceiver.exe`. Windows Defender peut demander une autorisation réseau privé. N'autorise pas les réseaux publics.
3. Note l'IP locale du PC (`ipconfig`), le code à six chiffres et l'empreinte TLS affichés dans la fenêtre. La clé et le certificat sont enregistrés dans `%LOCALAPPDATA%\CR3ATIX-MultiControl`.
4. Sur Android, installe l'APK debug, ouvre l'application, saisis les trois valeurs et touche **CONNECTER**. Compare l'empreinte caractère par caractère ; n'accepte jamais une empreinte inattendue.
5. Utilise le touchpad, le clavier et la dictée. **LOCAL ↔ DISTANT** revient au contrôle Android. Le Receiver doit rester visible et lancé.
6. Pour couper les connexions et changer de code, clique **TOUT DÉCONNECTER** sur Windows.

Le code est nécessaire à chaque nouvelle connexion ; l'application conserve seulement l'adresse et l'empreinte du PC. Les événements clavier et souris ne sont reçus par l'application Android que lorsqu'elle a le focus et, pour les mouvements physiques, que Pointer Capture est actif. Aucun accès à distance Internet n'est configuré. Le texte envoyé passe temporairement par le presse-papiers Windows pour l'insertion Unicode : ne l'utilise pas pour des secrets.

**Non livré dans ce build :** Android Receiver, plusieurs cibles simultanées, layout FOLLOW intégré, service d'arrière-plan, transfert de fichiers, macros et release stable. Ces fonctions restent à développer et tester avant d'appeler ce projet V1.
