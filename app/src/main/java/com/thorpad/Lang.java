package com.thorpad;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The keyboard language, which also sets the language of ThorPad's own buttons and settings.
 * Text is looked up by its English wording; anything missing stays in English.
 */
final class Lang {
    static final String[] CODES = {"en", "es", "fr", "de", "pt", "it"};
    /** Each language's name in itself, for the picker and the space bar. */
    static final String[] NAMES = {"English", "Español", "Français", "Deutsch", "Português", "Italiano"};

    private static volatile String code = "en";
    private static volatile int index;
    private static final Map<String, String[]> TABLE = new HashMap<>(400);

    private Lang() {}

    /** Reads the saved language; call once when an activity or service starts. */
    static void init(Context context) {
        set(new Prefs(context).language());
    }

    static void set(String c) {
        int i = indexOf(c);
        index = i;
        code = CODES[i];
    }

    static String code() { return code; }

    static int indexOf(String c) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i].equals(c)) return i;
        return 0;
    }

    static String name() { return NAMES[index]; }

    static Locale locale() { return new Locale(code); }

    /** en in the current language. */
    static String t(String en) {
        int i = index;
        if (i == 0 || en == null) return en;
        String[] tr = TABLE.get(en);
        return tr == null || tr[i - 1] == null ? en : tr[i - 1];
    }

    /** Translates a pattern with %s / %d holes, then fills them. */
    static String f(String en, Object... args) {
        return String.format(Locale.US, t(en), args);
    }

    private static void e(String en, String es, String fr, String de, String pt, String it) {
        TABLE.put(en, new String[] {es, fr, de, pt, it});
    }

    static {
        // ---- Menu buttons and trackpad screen ----
        e("Keyboard", "Teclado", "Clavier", "Tastatur", "Teclado", "Tastiera");
        e("PC keys", "Teclas PC", "Touches PC", "PC-Tasten", "Teclas PC", "Tasti PC");
        e("Settings", "Ajustes", "Réglages", "Einstellungen", "Definições", "Impostazioni");
        e("Close", "Cerrar", "Fermer", "Schließen", "Fechar", "Chiudi");
        e("PC controls when Moonlight, Steam Link, Parsec or another PC streaming app is on the top screen",
                "Controles de PC cuando Moonlight, Steam Link, Parsec u otra app de streaming de PC está en la pantalla de arriba",
                "Commandes PC quand Moonlight, Steam Link, Parsec ou une autre app de streaming PC est sur l'écran du haut",
                "PC-Steuerung, wenn Moonlight, Steam Link, Parsec oder eine andere PC-Streaming-App oben läuft",
                "Controlos de PC quando Moonlight, Steam Link, Parsec ou outra app de streaming de PC está no ecrã de cima",
                "Controlli PC quando Moonlight, Steam Link, Parsec o un'altra app di streaming PC è sullo schermo in alto");
        e("Recent apps, close app, Android back",
                "Apps recientes, cerrar app, atrás de Android",
                "Apps récentes, fermer l'app, retour Android",
                "Letzte Apps, App schließen, Android-Zurück",
                "Apps recentes, fechar app, voltar do Android",
                "App recenti, chiudi app, indietro di Android");
        e("Task view, desktop, Alt+Tab, Alt+← back",
                "Vista de tareas, escritorio, Alt+Tab, Alt+← atrás",
                "Vue des tâches, bureau, Alt+Tab, Alt+← retour",
                "Aufgabenansicht, Desktop, Alt+Tab, Alt+← zurück",
                "Vista de tarefas, ambiente de trabalho, Alt+Tab, Alt+← voltar",
                "Visualizzazione attività, desktop, Alt+Tab, Alt+← indietro");
        e("✕ Stop PC for %s", "✕ Quitar PC para %s", "✕ Arrêter PC pour %s", "✕ Kein PC für %s",
                "✕ Parar PC para %s", "✕ Togli PC per %s");
        e("➕ Always PC for %s", "➕ Siempre PC para %s", "➕ Toujours PC pour %s", "➕ Immer PC für %s",
                "➕ Sempre PC para %s", "➕ Sempre PC per %s");
        e("Use Android controls for this app again",
                "Volver a usar controles de Android en esta app",
                "Utiliser à nouveau les commandes Android pour cette app",
                "Für diese App wieder Android-Steuerung verwenden",
                "Voltar a usar controlos do Android nesta app",
                "Usa di nuovo i controlli Android per questa app");
        e("Treat this app as a PC streaming app in Auto",
                "Tratar esta app como app de streaming de PC en Auto",
                "Traiter cette app comme une app de streaming PC en Auto",
                "Diese App in Auto als PC-Streaming-App behandeln",
                "Tratar esta app como app de streaming de PC em Auto",
                "Tratta questa app come app di streaming PC in Auto");
        e("🏠 Go home", "🏠 Inicio", "🏠 Accueil", "🏠 Startbildschirm", "🏠 Início", "🏠 Home");
        e("Android home screen on the top screen",
                "Pantalla de inicio de Android en la pantalla de arriba",
                "Écran d'accueil Android sur l'écran du haut",
                "Android-Startbildschirm auf dem oberen Bildschirm",
                "Ecrã inicial do Android no ecrã de cima",
                "Schermata Home di Android sullo schermo in alto");
        e("PC keys need mouse mode (Shizuku)",
                "Las teclas PC necesitan el modo ratón (Shizuku)",
                "Les touches PC ont besoin du mode souris (Shizuku)",
                "PC-Tasten brauchen den Mausmodus (Shizuku)",
                "As teclas PC precisam do modo rato (Shizuku)",
                "I tasti PC richiedono la modalità mouse (Shizuku)");
        e("Downloading the update…", "Descargando la actualización…", "Téléchargement de la mise à jour…",
                "Update wird heruntergeladen…", "A transferir a atualização…", "Download dell'aggiornamento…");
        e("Real mouse mode (needed for GameNative games)",
                "Modo ratón real (necesario para los juegos de GameNative)",
                "Mode souris réelle (nécessaire pour les jeux GameNative)",
                "Echter Mausmodus (nötig für GameNative-Spiele)",
                "Modo rato real (necessário para os jogos do GameNative)",
                "Modalità mouse reale (necessaria per i giochi GameNative)");
        e("Allow ThorPad in Shizuku", "Permitir ThorPad en Shizuku", "Autoriser ThorPad dans Shizuku",
                "ThorPad in Shizuku erlauben", "Permitir ThorPad no Shizuku", "Consenti ThorPad in Shizuku");
        e("\nOr touch mode (no Shizuku; fine for normal Android apps, but games in GameNative won't get real mouse or scroll-wheel input)",
                "\nO modo táctil (sin Shizuku; bien para apps normales de Android, pero los juegos de GameNative no recibirán ratón ni rueda reales)",
                "\nOu mode tactile (sans Shizuku ; bien pour les apps Android normales, mais les jeux GameNative n'auront pas de vraie souris ni de molette)",
                "\nOder Touch-Modus (ohne Shizuku; gut für normale Android-Apps, aber GameNative-Spiele bekommen keine echte Maus und kein Mausrad)",
                "\nOu modo tátil (sem Shizuku; bom para apps normais do Android, mas os jogos do GameNative não recebem rato nem roda reais)",
                "\nOppure modalità touch (senza Shizuku; va bene per le normali app Android, ma i giochi GameNative non ricevono mouse e rotellina reali)");
        e("Turn on \"ThorPad trackpad\" in Accessibility settings. If Android says the setting is restricted: Settings > Apps > ThorPad > ⋮ > Allow restricted settings, then try again.",
                "Activa \"ThorPad trackpad\" en los ajustes de Accesibilidad. Si Android dice que el ajuste está restringido: Ajustes > Aplicaciones > ThorPad > ⋮ > Permitir ajustes restringidos, y vuelve a intentarlo.",
                "Activez « ThorPad trackpad » dans les paramètres d'accessibilité. Si Android indique que le paramètre est restreint : Paramètres > Applications > ThorPad > ⋮ > Autoriser les paramètres restreints, puis réessayez.",
                "Schalte „ThorPad trackpad“ in den Bedienungshilfen ein. Wenn Android sagt, die Einstellung ist eingeschränkt: Einstellungen > Apps > ThorPad > ⋮ > Eingeschränkte Einstellungen zulassen, dann erneut versuchen.",
                "Ative \"ThorPad trackpad\" nas definições de Acessibilidade. Se o Android disser que a definição está restrita: Definições > Apps > ThorPad > ⋮ > Permitir definições restritas e tente de novo.",
                "Attiva \"ThorPad trackpad\" nelle impostazioni di Accessibilità. Se Android dice che l'impostazione è limitata: Impostazioni > App > ThorPad > ⋮ > Consenti impostazioni limitate, poi riprova.");
        e("Accessibility settings", "Ajustes de accesibilidad", "Paramètres d'accessibilité",
                "Bedienungshilfen", "Definições de acessibilidade", "Impostazioni di accessibilità");
        e("ThorPad app info", "Info de la app ThorPad", "Infos sur l'app ThorPad", "ThorPad-App-Info",
                "Informações da app ThorPad", "Info app ThorPad");
        e("Mouse mode", "Modo ratón", "Mode souris", "Mausmodus", "Modo rato", "Modalità mouse");
        e("Touch mode", "Modo táctil", "Mode tactile", "Touch-Modus", "Modo tátil", "Modalità touch");
        e("Touch mode (virtual mouse failed)", "Modo táctil (falló el ratón virtual)",
                "Mode tactile (échec de la souris virtuelle)", "Touch-Modus (virtuelle Maus fehlgeschlagen)",
                "Modo tátil (o rato virtual falhou)", "Modalità touch (mouse virtuale non riuscito)");
        e("Starting mouse…", "Iniciando el ratón…", "Démarrage de la souris…", "Maus wird gestartet…",
                "A iniciar o rato…", "Avvio del mouse…");
        e("Mouse mode · PC controls", "Modo ratón · controles de PC", "Mode souris · commandes PC",
                "Mausmodus · PC-Steuerung", "Modo rato · controlos de PC", "Modalità mouse · controlli PC");
        e("The virtual mouse couldn't start: %s", "El ratón virtual no pudo iniciarse: %s",
                "La souris virtuelle n'a pas pu démarrer : %s", "Die virtuelle Maus konnte nicht starten: %s",
                "O rato virtual não arrancou: %s", "Il mouse virtuale non è partito: %s");
        e("Try again", "Reintentar", "Réessayer", "Erneut versuchen", "Tentar de novo", "Riprova");
        e("Install Shizuku, start it (Wireless debugging works without a PC), then reopen ThorPad.",
                "Instala Shizuku, inícialo (la depuración inalámbrica funciona sin PC) y vuelve a abrir ThorPad.",
                "Installez Shizuku, démarrez-le (le débogage sans fil marche sans PC), puis rouvrez ThorPad.",
                "Installiere Shizuku, starte es (Debugging über WLAN geht ohne PC) und öffne ThorPad erneut.",
                "Instale o Shizuku, inicie-o (a depuração sem fios funciona sem PC) e volte a abrir o ThorPad.",
                "Installa Shizuku, avvialo (il debug wireless funziona senza PC), poi riapri ThorPad.");
        e("Open Shizuku", "Abrir Shizuku", "Ouvrir Shizuku", "Shizuku öffnen", "Abrir Shizuku", "Apri Shizuku");
        e("Shizuku is running. Allow ThorPad to use it.",
                "Shizuku está activo. Permite que ThorPad lo use.",
                "Shizuku est lancé. Autorisez ThorPad à l'utiliser.",
                "Shizuku läuft. Erlaube ThorPad, es zu nutzen.",
                "O Shizuku está ativo. Permita que o ThorPad o use.",
                "Shizuku è attivo. Consenti a ThorPad di usarlo.");
        e("Swipe down from the top for the menu", "Desliza hacia abajo desde arriba para ver el menú",
                "Balayez vers le bas depuis le haut pour le menu", "Für das Menü von oben nach unten wischen",
                "Deslize para baixo a partir do topo para ver o menu", "Scorri in giù dall'alto per il menu");
        e("Ready.", "Listo.", "Prêt.", "Bereit.", "Pronto.", "Pronto.");

        // ---- Gesture hints ----
        e("☝️ One finger", "☝️ Un dedo", "☝️ Un doigt", "☝️ Ein Finger", "☝️ Um dedo", "☝️ Un dito");
        e("✌️ Two fingers", "✌️ Dos dedos", "✌️ Deux doigts", "✌️ Zwei Finger", "✌️ Dois dedos", "✌️ Due dita");
        e("🤟 Three fingers", "🤟 Tres dedos", "🤟 Trois doigts", "🤟 Drei Finger", "🤟 Três dedos", "🤟 Tre dita");
        e("Slide", "Deslizar", "Glisser", "Wischen", "Deslizar", "Scorri");
        e("move", "mover", "déplacer", "bewegen", "mover", "muovi");
        e("Tap", "Toque", "Appui", "Tippen", "Toque", "Tocco");
        e("click", "clic", "clic", "Klick", "clique", "clic");
        e("Hold", "Mantener", "Maintenir", "Halten", "Manter", "Tieni premuto");
        e("right click", "clic derecho", "clic droit", "Rechtsklick", "clique direito", "clic destro");
        e("Tap, hold, slide", "Toca, mantén, desliza", "Appui, maintien, glisser", "Tippen, halten, wischen",
                "Toca, mantém, desliza", "Tocca, tieni, scorri");
        e("drag", "arrastrar", "glisser-déposer", "ziehen", "arrastar", "trascina");
        e("Up / down", "Arriba / abajo", "Haut / bas", "Hoch / runter", "Cima / baixo", "Su / giù");
        e("scroll", "desplazar", "défiler", "scrollen", "deslocar", "scorri");
        e("Swipe right", "Deslizar a la derecha", "Balayer à droite", "Nach rechts wischen",
                "Deslizar para a direita", "Scorri a destra");
        e("back", "atrás", "retour", "zurück", "voltar", "indietro");
        e("Swipe left", "Deslizar a la izquierda", "Balayer à gauche", "Nach links wischen",
                "Deslizar para a esquerda", "Scorri a sinistra");
        e("forward", "adelante", "suivant", "vorwärts", "avançar", "avanti");
        e("Swipe up", "Deslizar arriba", "Balayer vers le haut", "Nach oben wischen",
                "Deslizar para cima", "Scorri in su");
        e("recent apps", "apps recientes", "apps récentes", "letzte Apps", "apps recentes", "app recenti");
        e("Swipe down", "Deslizar abajo", "Balayer vers le bas", "Nach unten wischen",
                "Deslizar para baixo", "Scorri in giù");
        e("close app", "cerrar app", "fermer l'app", "App schließen", "fechar app", "chiudi app");
        e("back (Alt+←)", "atrás (Alt+←)", "retour (Alt+←)", "zurück (Alt+←)", "voltar (Alt+←)", "indietro (Alt+←)");
        e("forward (Alt+→)", "adelante (Alt+→)", "suivant (Alt+→)", "vorwärts (Alt+→)", "avançar (Alt+→)",
                "avanti (Alt+→)");
        e("task view", "vista de tareas", "vue des tâches", "Aufgabenansicht", "vista de tarefas",
                "visualizzazione attività");
        e("show desktop", "mostrar escritorio", "afficher le bureau", "Desktop zeigen",
                "mostrar ambiente de trabalho", "mostra desktop");
        e("Swipe left / right", "Deslizar izquierda / derecha", "Balayer gauche / droite",
                "Links / rechts wischen", "Deslizar esquerda / direita", "Scorri sinistra / destra");
        e("switch app", "cambiar de app", "changer d'app", "App wechseln", "mudar de app", "cambia app");

        // ---- Keyboards ----
        e("✕ Close", "✕ Cerrar", "✕ Fermer", "✕ Schließen", "✕ Fechar", "✕ Chiudi");
        e("⏎ Enter", "⏎ Intro", "⏎ Entrée", "⏎ Enter", "⏎ Enter", "⏎ Invio");
        e("Clear all", "Borrar todo", "Tout effacer", "Alles löschen", "Apagar tudo", "Cancella tutto");
        e("Not connected to the top screen", "Sin conexión con la pantalla de arriba",
                "Pas connecté à l'écran du haut", "Nicht mit dem oberen Bildschirm verbunden",
                "Sem ligação ao ecrã de cima", "Non collegato allo schermo in alto");
        e("Typing to top screen", "Escribiendo en la pantalla de arriba", "Saisie sur l'écran du haut",
                "Tippen auf dem oberen Bildschirm", "A escrever no ecrã de cima", "Scrittura sullo schermo in alto");
        e("desktop", "escritorio", "bureau", "Desktop", "ambiente de trabalho", "desktop");
        e("task manager", "admin. de tareas", "gest. des tâches", "Task-Manager", "gestor de tarefas",
                "gestione attività");
        e("screenshot", "captura", "capture", "Screenshot", "captura", "screenshot");
        e("held", "pulsada", "maintenue", "gehalten", "premida", "premuto");
        e("Ctrl, Alt, Shift and Win stay held for the next key",
                "Ctrl, Alt, Shift y Win se mantienen para la siguiente tecla",
                "Ctrl, Alt, Maj et Win restent maintenues pour la touche suivante",
                "Strg, Alt, Umschalt und Win bleiben für die nächste Taste gedrückt",
                "Ctrl, Alt, Shift e Win ficam premidas para a próxima tecla",
                "Ctrl, Alt, Maiusc e Win restano premuti per il tasto successivo");
        e("tap a key to finish the combo", "toca una tecla para terminar la combinación",
                "appuyez sur une touche pour finir le raccourci", "tippe eine Taste, um die Kombination abzuschließen",
                "toque numa tecla para terminar a combinação", "tocca un tasto per completare la combinazione");

        // ---- Settings ----
        e("ThorPad settings", "Ajustes de ThorPad", "Réglages de ThorPad", "ThorPad-Einstellungen",
                "Definições do ThorPad", "Impostazioni di ThorPad");
        e("Pointer speed", "Velocidad del puntero", "Vitesse du pointeur", "Zeigergeschwindigkeit",
                "Velocidade do ponteiro", "Velocità del puntatore");
        e("Pointer acceleration", "Aceleración del puntero", "Accélération du pointeur", "Zeigerbeschleunigung",
                "Aceleração do ponteiro", "Accelerazione del puntatore");
        e("Scroll speed", "Velocidad de desplazamiento", "Vitesse de défilement", "Scrollgeschwindigkeit",
                "Velocidade de deslocamento", "Velocità di scorrimento");
        e("Pointer size", "Tamaño del puntero", "Taille du pointeur", "Zeigergröße", "Tamanho do ponteiro",
                "Dimensione del puntatore");
        e("Classic scroll direction (fingers down scrolls down, like a mouse wheel)",
                "Dirección de desplazamiento clásica (dedos hacia abajo bajan, como una rueda de ratón)",
                "Sens de défilement classique (doigts vers le bas = défiler vers le bas, comme une molette)",
                "Klassische Scrollrichtung (Finger nach unten scrollt nach unten, wie ein Mausrad)",
                "Direção de deslocamento clássica (dedos para baixo descem, como a roda do rato)",
                "Direzione di scorrimento classica (dita in giù scorre in giù, come la rotellina)");
        e("Swap screens (pointer on bottom, trackpad on top). Reopen ThorPad after changing.",
                "Intercambiar pantallas (puntero abajo, trackpad arriba). Vuelve a abrir ThorPad tras cambiarlo.",
                "Inverser les écrans (pointeur en bas, trackpad en haut). Rouvrez ThorPad après le changement.",
                "Bildschirme tauschen (Zeiger unten, Trackpad oben). Danach ThorPad neu öffnen.",
                "Trocar ecrãs (ponteiro em baixo, trackpad em cima). Volte a abrir o ThorPad depois de mudar.",
                "Scambia schermi (puntatore in basso, trackpad in alto). Riapri ThorPad dopo la modifica.");
        e("Dim the trackpad screen after 30 seconds without a touch, to save battery (the first touch only wakes it)",
                "Oscurecer la pantalla del trackpad tras 30 segundos sin tocarla, para ahorrar batería (el primer toque solo la despierta)",
                "Assombrir l'écran du trackpad après 30 secondes sans contact, pour économiser la batterie (le premier contact le réveille seulement)",
                "Trackpad-Bildschirm nach 30 Sekunden ohne Berührung abdunkeln, um Akku zu sparen (die erste Berührung weckt ihn nur)",
                "Escurecer o ecrã do trackpad após 30 segundos sem toque, para poupar bateria (o primeiro toque só o acorda)",
                "Oscura lo schermo del trackpad dopo 30 secondi senza tocchi, per risparmiare batteria (il primo tocco lo risveglia soltanto)");
        e("Show gesture hints on the trackpad", "Mostrar ayudas de gestos en el trackpad",
                "Afficher l'aide des gestes sur le trackpad", "Gestenhinweise auf dem Trackpad zeigen",
                "Mostrar dicas de gestos no trackpad", "Mostra i suggerimenti dei gesti sul trackpad");
        e("\nKeyboard", "\nTeclado", "\nClavier", "\nTastatur", "\nTeclado", "\nTastiera");
        e("Keyboard language", "Idioma del teclado", "Langue du clavier", "Tastatursprache",
                "Idioma do teclado", "Lingua della tastiera");
        e("Changes the letters on the keyboard, the spelling dictionary, and ThorPad's buttons and settings.",
                "Cambia las letras del teclado, el diccionario y los botones y ajustes de ThorPad.",
                "Change les lettres du clavier, le dictionnaire, et les boutons et réglages de ThorPad.",
                "Ändert die Tasten der Tastatur, das Wörterbuch sowie die Schaltflächen und Einstellungen von ThorPad.",
                "Muda as letras do teclado, o dicionário e os botões e definições do ThorPad.",
                "Cambia le lettere della tastiera, il dizionario e i pulsanti e le impostazioni di ThorPad.");
        e("Autocorrect (fixes a misspelled word when you finish it, splits words that ran together, adds apostrophes, and uses the word before to pick the right one; tap the suggestion to keep what you typed, or press backspace right after to undo)",
                "Autocorrección (corrige una palabra mal escrita al terminarla, separa palabras que quedaron juntas y añade acentos; toca la sugerencia para conservar lo que escribiste, o pulsa borrar justo después para deshacer)",
                "Correction automatique (corrige un mot mal écrit quand vous le terminez, sépare les mots collés et ajoute les accents ; touchez la suggestion pour garder ce que vous avez tapé, ou effacez juste après pour annuler)",
                "Autokorrektur (korrigiert ein falsch geschriebenes Wort, wenn du es beendest, trennt zusammengeschriebene Wörter und ergänzt Umlaute; tippe auf den Vorschlag, um deine Eingabe zu behalten, oder lösche direkt danach, um es rückgängig zu machen)",
                "Correção automática (corrige uma palavra mal escrita quando a termina, separa palavras juntas e põe acentos; toque na sugestão para manter o que escreveu, ou apague logo a seguir para anular)",
                "Correzione automatica (corregge una parola sbagliata quando la finisci, separa le parole attaccate e aggiunge gli accenti; tocca il suggerimento per tenere ciò che hai scritto, o premi cancella subito dopo per annullare)");
        e("Double-space period (two quick spaces type \". \")",
                "Punto con doble espacio (dos espacios rápidos escriben \". \")",
                "Point avec double espace (deux espaces rapides tapent « . »)",
                "Punkt per Doppel-Leertaste (zwei schnelle Leerzeichen tippen „. “)",
                "Ponto com espaço duplo (dois espaços rápidos escrevem \". \")",
                "Punto con doppio spazio (due spazi veloci scrivono \". \")");
        e("Auto-capitals (capital letter at the start of each sentence; never in email or password boxes)",
                "Mayúsculas automáticas (mayúscula al empezar cada frase; nunca en campos de correo o contraseña)",
                "Majuscules automatiques (majuscule au début de chaque phrase ; jamais dans les champs e-mail ou mot de passe)",
                "Automatische Großschreibung (Großbuchstabe am Satzanfang; nie in E-Mail- oder Passwortfeldern)",
                "Maiúsculas automáticas (maiúscula no início de cada frase; nunca em campos de email ou palavra-passe)",
                "Maiuscole automatiche (maiuscola all'inizio di ogni frase; mai nei campi email o password)");
        e("Type when the finger lifts (lets you slide to the right key; off = types the instant you touch, most responsive)",
                "Escribir al levantar el dedo (permite deslizar hasta la tecla correcta; desactivado = escribe al tocar, más rápido)",
                "Taper quand le doigt se lève (permet de glisser vers la bonne touche ; désactivé = tape dès le contact, plus réactif)",
                "Beim Loslassen tippen (du kannst zur richtigen Taste gleiten; aus = tippt sofort bei Berührung, am schnellsten)",
                "Escrever ao levantar o dedo (permite deslizar até à tecla certa; desligado = escreve ao tocar, mais rápido)",
                "Scrivi quando sollevi il dito (puoi scivolare sul tasto giusto; disattivato = scrive al tocco, più reattivo)");
        e("Hold a letter for its accents.", "Mantén pulsada una letra para ver sus acentos.",
                "Maintenez une lettre pour ses accents.", "Halte einen Buchstaben für Akzente und Umlaute gedrückt.",
                "Mantenha uma letra premida para ver os acentos.", "Tieni premuta una lettera per gli accenti.");
        e("\nGestures\n"
                        + "• One finger: move the pointer\n"
                        + "• Tap: click\n"
                        + "• Tap, then quickly touch again, pause a moment, and slide: drag\n"
                        + "• Press and hold: long press\n"
                        + "• Two fingers up or down: scroll\n"
                        + "• Two fingers swiped right: back; swiped left: forward\n"
                        + "• Three fingers swiped up: recent apps; swiped down: close the top-screen app\n"
                        + "• Two-finger tap: back\n"
                        + "• Swipe down from the top edge: show the menu buttons",
                "\nGestos\n"
                        + "• Un dedo: mover el puntero\n"
                        + "• Toque: clic\n"
                        + "• Toca, vuelve a tocar rápido, espera un momento y desliza: arrastrar\n"
                        + "• Mantener pulsado: pulsación larga\n"
                        + "• Dos dedos arriba o abajo: desplazar\n"
                        + "• Dos dedos a la derecha: atrás; a la izquierda: adelante\n"
                        + "• Tres dedos arriba: apps recientes; abajo: cerrar la app de arriba\n"
                        + "• Toque con dos dedos: atrás\n"
                        + "• Desliza hacia abajo desde el borde superior: mostrar los botones del menú",
                "\nGestes\n"
                        + "• Un doigt : déplacer le pointeur\n"
                        + "• Appui : clic\n"
                        + "• Appuyez, retouchez vite, attendez un instant et glissez : glisser-déposer\n"
                        + "• Appui long : appui prolongé\n"
                        + "• Deux doigts vers le haut ou le bas : défiler\n"
                        + "• Deux doigts à droite : retour ; à gauche : suivant\n"
                        + "• Trois doigts vers le haut : apps récentes ; vers le bas : fermer l'app du haut\n"
                        + "• Appui à deux doigts : retour\n"
                        + "• Balayez vers le bas depuis le bord supérieur : afficher les boutons du menu",
                "\nGesten\n"
                        + "• Ein Finger: Zeiger bewegen\n"
                        + "• Tippen: Klick\n"
                        + "• Tippen, schnell erneut berühren, kurz warten und wischen: ziehen\n"
                        + "• Gedrückt halten: langes Drücken\n"
                        + "• Zwei Finger hoch oder runter: scrollen\n"
                        + "• Zwei Finger nach rechts: zurück; nach links: vorwärts\n"
                        + "• Drei Finger nach oben: letzte Apps; nach unten: obere App schließen\n"
                        + "• Mit zwei Fingern tippen: zurück\n"
                        + "• Vom oberen Rand nach unten wischen: Menüschaltflächen zeigen",
                "\nGestos\n"
                        + "• Um dedo: mover o ponteiro\n"
                        + "• Toque: clique\n"
                        + "• Toque, volte a tocar depressa, espere um momento e deslize: arrastar\n"
                        + "• Manter premido: toque longo\n"
                        + "• Dois dedos para cima ou para baixo: deslocar\n"
                        + "• Dois dedos para a direita: voltar; para a esquerda: avançar\n"
                        + "• Três dedos para cima: apps recentes; para baixo: fechar a app de cima\n"
                        + "• Toque com dois dedos: voltar\n"
                        + "• Deslize para baixo a partir do topo: mostrar os botões do menu",
                "\nGesti\n"
                        + "• Un dito: muovi il puntatore\n"
                        + "• Tocco: clic\n"
                        + "• Tocca, ritocca subito, aspetta un attimo e scorri: trascina\n"
                        + "• Tieni premuto: pressione lunga\n"
                        + "• Due dita su o giù: scorri\n"
                        + "• Due dita a destra: indietro; a sinistra: avanti\n"
                        + "• Tre dita in su: app recenti; in giù: chiudi l'app in alto\n"
                        + "• Tocco con due dita: indietro\n"
                        + "• Scorri in giù dal bordo superiore: mostra i pulsanti del menu");
        e("This is Android's own keyboard setting for the whole Thor. With ThorPad keyboard in use, the keyboard on the bottom screen opens by itself whenever a text box is selected on the top screen, with no other keyboard popping up.",
                "Este es el ajuste de teclado de Android para toda la Thor. Con el teclado ThorPad en uso, el teclado de la pantalla de abajo se abre solo cuando se selecciona un cuadro de texto arriba, sin que aparezca otro teclado.",
                "C'est le réglage de clavier d'Android pour toute la Thor. Avec le clavier ThorPad, le clavier de l'écran du bas s'ouvre tout seul dès qu'un champ de texte est sélectionné en haut, sans autre clavier.",
                "Das ist Androids eigene Tastatureinstellung für den ganzen Thor. Mit der ThorPad-Tastatur öffnet sich die Tastatur unten von selbst, sobald oben ein Textfeld ausgewählt wird, ohne dass eine andere Tastatur erscheint.",
                "Esta é a definição de teclado do Android para toda a Thor. Com o teclado ThorPad em uso, o teclado do ecrã de baixo abre sozinho quando se seleciona uma caixa de texto em cima, sem aparecer outro teclado.",
                "Questa è l'impostazione della tastiera di Android per tutta la Thor. Con la tastiera ThorPad in uso, la tastiera in basso si apre da sola quando selezioni una casella di testo in alto, senza altre tastiere.");
        e("Other keyboard", "Otro teclado", "Autre clavier", "Andere Tastatur", "Outro teclado", "Altra tastiera");
        e("unknown", "desconocido", "inconnu", "unbekannt", "desconhecido", "sconosciuta");
        e("Android keyboard: %s", "Teclado de Android: %s", "Clavier Android : %s", "Android-Tastatur: %s",
                "Teclado do Android: %s", "Tastiera Android: %s");
        e("ThorPad keyboard in use", "Teclado ThorPad en uso", "Clavier ThorPad utilisé",
                "ThorPad-Tastatur aktiv", "Teclado ThorPad em uso", "Tastiera ThorPad in uso");
        e("Use ThorPad keyboard", "Usar teclado ThorPad", "Utiliser le clavier ThorPad",
                "ThorPad-Tastatur verwenden", "Usar teclado ThorPad", "Usa tastiera ThorPad");
        e("Keyboard for text boxes", "Teclado para cuadros de texto", "Clavier pour les champs de texte",
                "Tastatur für Textfelder", "Teclado para caixas de texto", "Tastiera per le caselle di testo");
        e("Cancel", "Cancelar", "Annuler", "Abbrechen", "Cancelar", "Annulla");
        e("%s is now Android's keyboard", "%s es ahora el teclado de Android", "%s est maintenant le clavier d'Android",
                "%s ist jetzt die Android-Tastatur", "%s é agora o teclado do Android", "%s è ora la tastiera di Android");
        e("Turn on %s here, then come back and choose it again.",
                "Activa %s aquí, luego vuelve y elígelo otra vez.",
                "Activez %s ici, puis revenez le choisir à nouveau.",
                "Schalte %s hier ein, komm dann zurück und wähle es erneut.",
                "Ative %s aqui, depois volte e escolha-o de novo.",
                "Attiva %s qui, poi torna e sceglilo di nuovo.");
        e("Needs Shizuku", "Necesita Shizuku", "Nécessite Shizuku", "Braucht Shizuku", "Precisa do Shizuku",
                "Richiede Shizuku");
        e("Saving screenshots…", "Guardando capturas…", "Enregistrement des captures…", "Screenshots werden gespeichert…",
                "A guardar capturas…", "Salvataggio degli screenshot…");
        e("Saved thorpad-screen, thorpad-windows and thorpad-anr files to Download",
                "Archivos thorpad-screen, thorpad-windows y thorpad-anr guardados en Download",
                "Fichiers thorpad-screen, thorpad-windows et thorpad-anr enregistrés dans Download",
                "Dateien thorpad-screen, thorpad-windows und thorpad-anr in Download gespeichert",
                "Ficheiros thorpad-screen, thorpad-windows e thorpad-anr guardados em Download",
                "File thorpad-screen, thorpad-windows e thorpad-anr salvati in Download");
        e("Couldn't save the screenshots", "No se pudieron guardar las capturas", "Impossible d'enregistrer les captures",
                "Screenshots konnten nicht gespeichert werden", "Não foi possível guardar as capturas",
                "Impossibile salvare gli screenshot");
        e("\nUpdates", "\nActualizaciones", "\nMises à jour", "\nUpdates", "\nAtualizações", "\nAggiornamenti");
        e("Check for updates", "Buscar actualizaciones", "Rechercher des mises à jour", "Nach Updates suchen",
                "Procurar atualizações", "Cerca aggiornamenti");
        e("Reinstall", "Reinstalar", "Réinstaller", "Neu installieren", "Reinstalar", "Reinstalla");
        e("\nHaptics", "\nVibración", "\nVibrations", "\nHaptik", "\nVibração", "\nVibrazione");
        e("Pick a feel and strength for each action. Every change plays a preview.",
                "Elige un tipo y una intensidad para cada acción. Cada cambio reproduce una muestra.",
                "Choisissez un ressenti et une intensité pour chaque action. Chaque changement joue un aperçu.",
                "Wähle Gefühl und Stärke für jede Aktion. Jede Änderung spielt eine Vorschau.",
                "Escolha um tipo e uma intensidade para cada ação. Cada alteração reproduz uma amostra.",
                "Scegli tipo e intensità per ogni azione. Ogni modifica riproduce un'anteprima.");
        e("This device's vibration motor can't buzz softer or harder, so strength changes how long each buzz lasts instead.",
                "El motor de vibración de este dispositivo no puede vibrar más suave o más fuerte, así que la intensidad cambia cuánto dura cada vibración.",
                "Le moteur de vibration de cet appareil ne peut pas vibrer plus ou moins fort, donc l'intensité change plutôt la durée de chaque vibration.",
                "Der Vibrationsmotor dieses Geräts kann nicht schwächer oder stärker vibrieren, daher ändert die Stärke stattdessen die Dauer.",
                "O motor de vibração deste aparelho não vibra mais suave nem mais forte, por isso a intensidade muda quanto tempo dura cada vibração.",
                "Il motore di vibrazione di questo dispositivo non può vibrare più piano o più forte, quindi l'intensità cambia la durata di ogni vibrazione.");
        e("Off", "Desactivado", "Désactivé", "Aus", "Desligado", "Disattivato");
        e("▶ Preview: %s", "▶ Muestra: %s", "▶ Aperçu : %s", "▶ Vorschau: %s", "▶ Amostra: %s", "▶ Anteprima: %s");
        // Haptics actions and feels
        e("Click", "Clic", "Clic", "Klick", "Clique", "Clic");
        e("Right click", "Clic derecho", "Clic droit", "Rechtsklick", "Clique direito", "Clic destro");
        e("Drag start", "Inicio de arrastre", "Début du glisser", "Ziehen beginnt", "Início de arrasto", "Inizio trascinamento");
        e("Drag end", "Fin de arrastre", "Fin du glisser", "Ziehen endet", "Fim de arrasto", "Fine trascinamento");
        e("Press and hold", "Mantener pulsado", "Appui long", "Gedrückt halten", "Manter premido", "Tieni premuto");
        e("Scroll wheel notch", "Paso de la rueda", "Cran de molette", "Mausrad-Raste", "Passo da roda", "Scatto rotellina");
        e("Keyboard key", "Tecla", "Touche du clavier", "Tastaturtaste", "Tecla", "Tasto");
        e("Top bar buttons", "Botones del menú", "Boutons du menu", "Menüschaltflächen", "Botões do menu", "Pulsanti del menu");
        e("Back / forward swipe", "Gesto atrás / adelante", "Balayage retour / suivant", "Zurück- / Vorwärts-Wischen",
                "Gesto voltar / avançar", "Gesto indietro / avanti");
        e("Three-finger swipe", "Gesto con tres dedos", "Balayage à trois doigts", "Drei-Finger-Wischen",
                "Gesto com três dedos", "Gesto a tre dita");
        e("Tick", "Tic", "Tic", "Tick", "Tique", "Tic");
        e("Heavy", "Fuerte", "Fort", "Kräftig", "Forte", "Forte");
        e("Double", "Doble", "Double", "Doppelt", "Duplo", "Doppio");
        e("Buzz", "Zumbido", "Bourdon", "Brummen", "Zumbido", "Ronzio");

        // ---- Settings tabs ----
        e("Trackpad", "Trackpad", "Trackpad", "Trackpad", "Trackpad", "Trackpad");
        e("How the pointer moves and scrolls on the top screen",
                "Cómo se mueve y desplaza el puntero en la pantalla de arriba",
                "Comment le pointeur se déplace et défile sur l'écran du haut",
                "Wie sich der Zeiger auf dem oberen Bildschirm bewegt und scrollt",
                "Como o ponteiro se move e desloca no ecrã de cima",
                "Come il puntatore si muove e scorre sullo schermo in alto");
        e("Classic scroll direction", "Desplazamiento clásico", "Défilement classique", "Klassische Scrollrichtung",
                "Deslocamento clássico", "Scorrimento classico");
        e("Fingers down scrolls down, like a mouse wheel", "Dedos hacia abajo bajan, como una rueda de ratón",
                "Doigts vers le bas = défiler vers le bas, comme une molette",
                "Finger nach unten scrollt nach unten, wie ein Mausrad",
                "Dedos para baixo descem, como a roda do rato", "Dita in giù scorre in giù, come la rotellina");
        e("Swap screens", "Intercambiar pantallas", "Inverser les écrans", "Bildschirme tauschen", "Trocar ecrãs",
                "Scambia schermi");
        e("Pointer on the bottom, trackpad on top. Reopen ThorPad after changing.",
                "Puntero abajo, trackpad arriba. Vuelve a abrir ThorPad tras cambiarlo.",
                "Pointeur en bas, trackpad en haut. Rouvrez ThorPad après le changement.",
                "Zeiger unten, Trackpad oben. Danach ThorPad neu öffnen.",
                "Ponteiro em baixo, trackpad em cima. Volte a abrir o ThorPad depois de mudar.",
                "Puntatore in basso, trackpad in alto. Riapri ThorPad dopo la modifica.");
        e("Dim when idle", "Oscurecer en reposo", "Assombrir au repos", "Im Leerlauf abdunkeln", "Escurecer em repouso",
                "Oscura quando inattivo");
        e("After 30 seconds without a touch, to save battery; the first touch only wakes it",
                "Tras 30 segundos sin tocar, para ahorrar batería; el primer toque solo la despierta",
                "Après 30 secondes sans contact, pour économiser la batterie ; le premier contact le réveille seulement",
                "Nach 30 Sekunden ohne Berührung, um Akku zu sparen; die erste Berührung weckt ihn nur",
                "Após 30 segundos sem toque, para poupar bateria; o primeiro toque só o acorda",
                "Dopo 30 secondi senza tocchi, per risparmiare batteria; il primo tocco lo risveglia soltanto");
        e("Gesture hints", "Ayudas de gestos", "Aide des gestes", "Gestenhinweise", "Dicas de gestos", "Suggerimenti gesti");
        e("Show the gesture cheat sheet on the trackpad", "Muestra la chuleta de gestos en el trackpad",
                "Affiche l'aide-mémoire des gestes sur le trackpad", "Zeigt die Gestenübersicht auf dem Trackpad",
                "Mostra o resumo de gestos no trackpad", "Mostra il promemoria dei gesti sul trackpad");
        e("Typing, spelling and which keyboard Android uses",
                "Escritura, ortografía y qué teclado usa Android",
                "Saisie, orthographe et clavier utilisé par Android",
                "Tippen, Rechtschreibung und welche Tastatur Android nutzt",
                "Escrita, ortografia e que teclado o Android usa",
                "Scrittura, ortografia e quale tastiera usa Android");
        e("Language", "Idioma", "Langue", "Sprache", "Idioma", "Lingua");
        e("Autocorrect", "Autocorrección", "Correction automatique", "Autokorrektur", "Correção automática",
                "Correzione automatica");
        e("Fixes misspelled and run-together words when you finish them. Tap the suggestion to keep what you typed, or press backspace right after to undo.",
                "Corrige palabras mal escritas o juntas al terminarlas. Toca la sugerencia para conservar lo que escribiste, o pulsa borrar justo después para deshacer.",
                "Corrige les mots mal écrits ou collés quand vous les terminez. Touchez la suggestion pour garder ce que vous avez tapé, ou effacez juste après pour annuler.",
                "Korrigiert falsch oder zusammengeschriebene Wörter, wenn du sie beendest. Tippe auf den Vorschlag, um deine Eingabe zu behalten, oder lösche direkt danach, um es rückgängig zu machen.",
                "Corrige palavras mal escritas ou juntas quando as termina. Toque na sugestão para manter o que escreveu, ou apague logo a seguir para anular.",
                "Corregge le parole sbagliate o attaccate quando le finisci. Tocca il suggerimento per tenere ciò che hai scritto, o premi cancella subito dopo per annullare.");
        e("Double-space period", "Punto con doble espacio", "Point avec double espace", "Punkt per Doppel-Leertaste",
                "Ponto com espaço duplo", "Punto con doppio spazio");
        e("Two quick spaces type \". \"", "Dos espacios rápidos escriben \". \"", "Deux espaces rapides tapent « . »",
                "Zwei schnelle Leerzeichen tippen „. “", "Dois espaços rápidos escrevem \". \"",
                "Due spazi veloci scrivono \". \"");
        e("Auto-capitals", "Mayúsculas automáticas", "Majuscules automatiques", "Automatische Großschreibung",
                "Maiúsculas automáticas", "Maiuscole automatiche");
        e("A capital letter at the start of each sentence, never in email or password boxes",
                "Mayúscula al empezar cada frase, nunca en campos de correo o contraseña",
                "Majuscule au début de chaque phrase, jamais dans les champs e-mail ou mot de passe",
                "Großbuchstabe am Satzanfang, nie in E-Mail- oder Passwortfeldern",
                "Maiúscula no início de cada frase, nunca em campos de email ou palavra-passe",
                "Maiuscola all'inizio di ogni frase, mai nei campi email o password");
        e("Type when the finger lifts", "Escribir al levantar el dedo", "Taper quand le doigt se lève",
                "Beim Loslassen tippen", "Escrever ao levantar o dedo", "Scrivi quando sollevi il dito");
        e("Lets you slide to the right key. Off types the instant you touch, which is quickest",
                "Permite deslizar hasta la tecla correcta. Desactivado escribe al tocar, que es lo más rápido",
                "Permet de glisser vers la bonne touche. Désactivé tape dès le contact, le plus rapide",
                "Du kannst zur richtigen Taste gleiten. Aus tippt sofort bei Berührung, am schnellsten",
                "Permite deslizar até à tecla certa. Desligado escreve ao tocar, o mais rápido",
                "Puoi scivolare sul tasto giusto. Disattivato scrive al tocco, il più veloce");
        e("Back", "Atrás", "Retour", "Zurück", "Voltar", "Indietro");
        e("Pick a feel and strength for each action.", "Elige un tipo y una intensidad para cada acción.",
                "Choisissez un ressenti et une intensité pour chaque action.", "Wähle Gefühl und Stärke für jede Aktion.",
                "Escolha um tipo e uma intensidade para cada ação.", "Scegli tipo e intensità per ogni azione.");
        e("Haptics", "Vibración", "Vibrations", "Haptik", "Vibração", "Vibrazione");
        e("Gestures", "Gestos", "Gestes", "Gesten", "Gestos", "Gesti");
        e("What each touch does on the trackpad", "Qué hace cada toque en el trackpad",
                "Ce que fait chaque geste sur le trackpad", "Was jede Berührung auf dem Trackpad bewirkt",
                "O que cada toque faz no trackpad", "Cosa fa ogni tocco sul trackpad");
        e("Updates", "Actualizaciones", "Mises à jour", "Updates", "Atualizações", "Aggiornamenti");
        e("Get the newest ThorPad from GitHub", "Consigue el ThorPad más reciente de GitHub",
                "Obtenez le dernier ThorPad sur GitHub", "Hol dir das neueste ThorPad von GitHub",
                "Obtenha o ThorPad mais recente do GitHub", "Scarica l'ultimo ThorPad da GitHub");

        // ---- Updates ----
        e("🔁 Reinstall ThorPad %s?", "🔁 ¿Reinstalar ThorPad %s?", "🔁 Réinstaller ThorPad %s ?",
                "🔁 ThorPad %s neu installieren?", "🔁 Reinstalar o ThorPad %s?", "🔁 Reinstallare ThorPad %s?");
        e("🎉 ThorPad %s is available", "🎉 ThorPad %s está disponible", "🎉 ThorPad %s est disponible",
                "🎉 ThorPad %s ist verfügbar", "🎉 ThorPad %s está disponível", "🎉 ThorPad %s è disponibile");
        e("Downloads this version again and installs it over the one you have. Settings are kept",
                "Vuelve a descargar esta versión y la instala sobre la que tienes. Los ajustes se conservan",
                "Retélécharge cette version et l'installe par-dessus la vôtre. Les réglages sont conservés",
                "Lädt diese Version erneut und installiert sie über deine. Einstellungen bleiben erhalten",
                "Volta a transferir esta versão e instala-a por cima da sua. As definições mantêm-se",
                "Scarica di nuovo questa versione e la installa sopra la tua. Le impostazioni restano");
        e("You have %s", "Tienes la %s", "Vous avez la %s", "Du hast %s", "Tem a %s", "Hai la %s");
        e("%s KB download", "descarga de %s KB", "téléchargement de %s Ko", "%s KB Download",
                "transferência de %s KB", "download di %s KB");
        e("Not now", "Ahora no", "Pas maintenant", "Nicht jetzt", "Agora não", "Non ora");
        e("Update", "Actualizar", "Mettre à jour", "Aktualisieren", "Atualizar", "Aggiorna");
        e("Checking for updates…", "Buscando actualizaciones…", "Recherche de mises à jour…",
                "Suche nach Updates…", "A procurar atualizações…", "Ricerca aggiornamenti…");
        e("ThorPad %s is up to date.", "ThorPad %s está actualizado.", "ThorPad %s est à jour.",
                "ThorPad %s ist aktuell.", "O ThorPad %s está atualizado.", "ThorPad %s è aggiornato.");
        e("Version %s is out, but its release has no APK yet.",
                "La versión %s ya salió, pero aún no tiene APK.",
                "La version %s est sortie, mais elle n'a pas encore d'APK.",
                "Version %s ist da, hat aber noch keine APK.",
                "A versão %s já saiu, mas ainda não tem APK.",
                "La versione %s è uscita, ma non ha ancora un APK.");
        e("Ready to reinstall ThorPad %s.", "Listo para reinstalar ThorPad %s.", "Prêt à réinstaller ThorPad %s.",
                "Bereit, ThorPad %s neu zu installieren.", "Pronto para reinstalar o ThorPad %s.",
                "Pronto per reinstallare ThorPad %s.");
        e("ThorPad %s is available.", "ThorPad %s está disponible.", "ThorPad %s est disponible.",
                "ThorPad %s ist verfügbar.", "O ThorPad %s está disponível.", "ThorPad %s è disponibile.");
        e("Installing %s… ThorPad will close and can be reopened.",
                "Instalando %s… ThorPad se cerrará y podrás volver a abrirlo.",
                "Installation de %s… ThorPad va se fermer et pourra être rouvert.",
                "%s wird installiert… ThorPad schließt sich und kann neu geöffnet werden.",
                "A instalar %s… O ThorPad vai fechar e pode voltar a abri-lo.",
                "Installazione di %s… ThorPad si chiuderà e potrai riaprirlo.");
        e("Installing %s… confirm on the install prompt.",
                "Instalando %s… confirma en el aviso de instalación.",
                "Installation de %s… confirmez dans la fenêtre d'installation.",
                "%s wird installiert… bestätige die Installationsabfrage.",
                "A instalar %s… confirme no aviso de instalação.",
                "Installazione di %s… conferma nella richiesta di installazione.");
        e("Update failed: %s", "La actualización falló: %s", "Échec de la mise à jour : %s",
                "Update fehlgeschlagen: %s", "A atualização falhou: %s", "Aggiornamento non riuscito: %s");
        e("Downloading %s… %s%%", "Descargando %s… %s%%", "Téléchargement de %s… %s %%", "%s wird geladen… %s %%",
                "A transferir %s… %s%%", "Download di %s… %s%%");
        e("Shizuku couldn't install it (%s), trying Android's installer.",
                "Shizuku no pudo instalarlo (%s), probando con el instalador de Android.",
                "Shizuku n'a pas pu l'installer (%s), essai avec l'installateur d'Android.",
                "Shizuku konnte es nicht installieren (%s), versuche Androids Installer.",
                "O Shizuku não conseguiu instalar (%s), a tentar o instalador do Android.",
                "Shizuku non è riuscito a installarlo (%s), provo con il programma di installazione di Android.");
    }
}
