const year = document.getElementById('year');
if (year) {
  year.textContent = new Date().getFullYear();
}

const revealElements = document.querySelectorAll('.reveal');

if ('IntersectionObserver' in window) {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add('in-view');
        }
      });
    },
    { threshold: 0.2 }
  );

  revealElements.forEach((element) => observer.observe(element));
} else {
  revealElements.forEach((element) => element.classList.add('in-view'));
}

const menuToggle = document.getElementById('menuToggle');
const siteNav = document.getElementById('siteNav');
const languageSelect = document.getElementById('languageSelect');

const translations = {
  en: {
    'nav.features': 'Features',
    'nav.tech': 'Tech',
    'nav.download': 'Download',
    'hero.eyebrow': 'Editing made easy',
    'hero.titleTail': 'is a clean, lightweight text editor for Android.',
    'hero.description': 'Built for focused writing and coding sessions with multi-tab editing, smart file workflows, and a polished dark interface.',
    'hero.github': 'View on GitHub',
    'hero.download': 'Download',
    'hero.caption': 'Dark theme designed for clarity.',
    'features.title': 'Features',
    'features.multiTab.title': 'Multi-tab editing',
    'features.multiTab.body': 'Work with several files at once without breaking your flow.',
    'features.fileManagement.title': 'File management',
    'features.fileManagement.body': 'Create, open, save, and save-as with smart auto-naming.',
    'features.unsaved.title': 'Unsaved change detection',
    'features.unsaved.body': 'Get prompted before closing or switching away from modified files.',
    'features.customizable.title': 'Customizable editor',
    'features.customizable.body': 'Choose font family, size, line wrap, auto-save, and default save folder.',
    'features.wakelock.title': 'WakeLock support',
    'features.wakelock.body': 'Keep your screen on while writing or reviewing long documents.',
    'features.language.title': 'Language support',
    'features.language.body': 'Available in English and Slovak.',
    'tech.title': 'Tech',
    'tech.android': 'Built against modern Android APIs with a minimum SDK of 26.',
    'tech.kotlin': 'Entire app written in Kotlin for concise and maintainable code.',
    'tech.gradle': 'Project configuration uses Gradle with Kotlin-based build scripts.',
    'gettingStarted.title': 'Getting started',
    'gettingStarted.body': 'Open the project in Android Studio, then build and run on a device or emulator.',
    'contrib.title': 'Contributing & License',
    'contrib.bodyPrefix': 'Want to contribute? See',
    'contrib.bodySuffix': 'for details, including translation guidelines.',
    'license.prefix': 'Koda is open source under the',
    'footer.copy': 'Koda by s17 Labs.',
    'footer.lab': 's17 Labs',
    'footer.github': 'GitHub'
  },
  sk: {
    'nav.features': 'Funkcie',
    'nav.tech': 'Technológie',
    'nav.download': 'Stiahnuť',
    'hero.eyebrow': 'Upravovanie bez námahy',
    'hero.titleTail': 'je čistý a ľahký textový editor pre Android.',
    'hero.description': 'Vytvorený pre sústredené písanie a kódovanie s úpravou vo viacerých kartách, inteligentnou prácou so súbormi a vyladeným tmavým rozhraním.',
    'hero.github': 'Zobraziť na GitHube',
    'hero.download': 'Stiahnuť',
    'hero.caption': 'Tmavý motív navrhnutý pre prehľadnosť.',
    'features.title': 'Funkcie',
    'features.multiTab.title': 'Úprava vo viacerých kartách',
    'features.multiTab.body': 'Pracujte s viacerými súbormi naraz bez prerušenia pracovného toku.',
    'features.fileManagement.title': 'Správa súborov',
    'features.fileManagement.body': 'Vytváranie, otváranie, ukladanie a uloženie ako s inteligentným automatickým pomenovaním.',
    'features.unsaved.title': 'Detekcia neuložených zmien',
    'features.unsaved.body': 'Pri zatváraní alebo prepnutí z upravených súborov dostanete upozornenie.',
    'features.customizable.title': 'Prispôsobiteľný editor',
    'features.customizable.body': 'Zvoľte písmo, veľkosť, zalamovanie riadkov, automatické ukladanie a predvolený priečinok.',
    'features.wakelock.title': 'Podpora WakeLock',
    'features.wakelock.body': 'Počas písania alebo prezerania dlhých dokumentov nechajte obrazovku zapnutú.',
    'features.language.title': 'Jazyková podpora',
    'features.language.body': 'Dostupné v angličtine a slovenčine.',
    'tech.title': 'Technológie',
    'tech.android': 'Aplikácia je postavená na moderných Android API s minimálnym SDK 26.',
    'tech.kotlin': 'Celá aplikácia je napísaná v Kotline pre stručný a udržiavateľný kód.',
    'tech.gradle': 'Konfigurácia projektu používa Gradle s Kotlin build skriptmi.',
    'gettingStarted.title': 'Ako začať',
    'gettingStarted.body': 'Otvorte projekt v Android Studiu a potom ho zostavte a spustite na zariadení alebo emulátore.',
    'contrib.title': 'Prispievanie a licencia',
    'contrib.bodyPrefix': 'Chcete prispieť? Pozrite si',
    'contrib.bodySuffix': 'pre podrobnosti vrátane pokynov k prekladom.',
    'license.prefix': 'Koda je open source pod licenciou',
    'footer.copy': 'Koda od s17 Labs.',
    'footer.lab': 's17 Labs',
    'footer.github': 'GitHub'
  }
};

const applyLanguage = (language) => {
  const dictionary = translations[language] || translations.en;
  document.documentElement.lang = language;
  document.querySelectorAll('[data-i18n]').forEach((element) => {
    const key = element.getAttribute('data-i18n');
    if (key && dictionary[key]) {
      element.textContent = dictionary[key];
    }
  });
  if (languageSelect) {
    languageSelect.value = language;
  }
};

const getStoredLanguage = () => {
  try {
    return localStorage.getItem('koda-language');
  } catch (error) {
    return null;
  }
};

const setStoredLanguage = (language) => {
  try {
    localStorage.setItem('koda-language', language);
  } catch (error) {
    // Ignore storage errors (e.g., blocked Web Storage).
  }
};

if (languageSelect) {
  const storedLanguage = getStoredLanguage();
  const browserLanguage = navigator.language?.toLowerCase().startsWith('sk') ? 'sk' : 'en';
  const initialLanguage = storedLanguage && translations[storedLanguage] ? storedLanguage : browserLanguage;
  applyLanguage(initialLanguage);

  languageSelect.addEventListener('change', (event) => {
    const selectedLanguage = event.target.value;
    applyLanguage(selectedLanguage);
    setStoredLanguage(selectedLanguage);
  });
}

if (menuToggle && siteNav) {
  menuToggle.addEventListener('click', () => {
    siteNav.classList.toggle('open');
    const isOpen = siteNav.classList.contains('open');
    menuToggle.setAttribute('aria-expanded', String(isOpen));
  });

  siteNav.querySelectorAll('a').forEach((link) => {
    link.addEventListener('click', () => {
      siteNav.classList.remove('open');
      menuToggle.setAttribute('aria-expanded', 'false');
    });
  });
}
