'use strict';

(function (global) {

  const STORAGE_KEY = 'sv_lang';

  const SUPPORTED_LANGS = {
    en: { label: 'English',   available: true  },
    es: { label: 'Español',   available: false },
    fr: { label: 'Français',  available: false },
    de: { label: 'Deutsch',   available: false },
    it: { label: 'Italiano',  available: false },
    pt: { label: 'Português', available: false },
    ja: { label: '日本語',     available: false },
    ko: { label: '한국어',     available: false },
    zh: { label: '中文',       available: false },
  };

  const STRINGS = {
    en: {
      nav: {
        library:     'Library',
        collections: 'Collections',
        downloads:   'Downloads',
        statistics:  'Statistics',
        accounts:    'Connected Accounts',
        settings:    'Settings',
        imports:     'Import Center',
        signOut:     'Sign Out',
      },
      vault: {
        title:    'Story Vault',
        tagline:  'Private Literary Archive',
        openBtn:  '▼ Open Vault',
        closeBtn: '▲ Close Vault',
      },
      status: {
        ONGOING:   'Ongoing',
        COMPLETE:  'Completed',
        HIATUS:    'Hiatus',
        ABANDONED: 'Abandoned',
      },
      readingStatus: {
        WANT_TO_READ:     'Want to Read',
        STILL_READING:    'Reading',
        CAUGHT_UP:        'Caught Up',
        FINISHED_READING: 'Finished',
        ON_HOLD:          'On Hold',
        DNF:              'DNF',
        REREADING:        'Rereading',
      },
      rating: {
        NOT_RATED: 'Not rated',
        GENERAL:   'General',
        TEEN:      'Teen',
        MATURE:    'Mature',
        EXPLICIT:  'Explicit',
      },
      platform: {
        AO3:     'AO3',
        FFN:     'FFN',
        WATTPAD: 'Wattpad',
        OTHER:   'Other',
      },
      kudos: {
        GIVEN:        '♥ Kudosed',
        NOT_DETECTED: 'Not given',
        UNKNOWN:      '',
      },
      ui: {
        loading:         'Loading…',
        save:            'Save',
        cancel:          'Cancel',
        delete:          'Delete',
        close:           'Close',
        edit:            'Edit',
        add:             'Add',
        remove:          'Remove',
        search:          'Search',
        filter:          'Filter',
        signOut:         'Sign Out',
        continueReading: 'Continue Reading',
        openOriginal:    'Open original',
        comingSoon:      'Coming soon',
        on:              'On',
        off:             'Off',
        noData:          'No data',
        fileAttached:    'File attached',
        kudosed:         'Kudosed',
      },
      filter: {
        any:          'Any',
        anyKudos:     'Any kudos',
        kudosGiven:   'Kudos given',
        kudosNotGiven:'Kudos not given',
        searchHint:   'Search titles, authors, tags, summaries…',
        ongoing:      'Ongoing',
        complete:     'Completed',
        hiatus:       'Hiatus',
        abandoned:    'Abandoned',
        wantToRead:   'Want to Read',
        stillReading: 'Reading',
        caughtUp:     'Caught Up',
        finished:     'Finished',
        onHold:       'On Hold',
        dnf:          'DNF',
        rereading:    'Rereading',
        notRated:     'Not rated',
        general:      'General',
        teen:         'Teen',
        mature:       'Mature',
        explicit:     'Explicit',
        platform:     'Platform',
        status:       'Status',
        readingStatus:'Reading status',
        kudos:        'Kudos',
        collection:   'Collection',
        all:          'All',
        asc:          'Ascending',
        desc:         'Descending',
      },
      page: {
        library: {
          empty:     'No stories in your vault yet.',
          noResults: 'No stories match the current filters.',
        },
        downloads: {
          title:     'Downloads',
          sub:       'Offline reading library',
          empty:     'No downloads saved yet.',
          emptyHint: 'Future downloads will appear here.',
        },
        collections: {
          title:  'Collections',
          sub:    'Curated reading lists',
          empty:  'No collections yet. Use the button above to create one.',
          manage: 'Manage Collections',
        },
        statistics: {
          title: 'Statistics',
          sub:   'Reading analytics and vault insights',
        },
        accounts: {
          title: 'Connected Accounts',
          sub:   'Reading platform accounts linked to your vault',
          empty: 'No connected accounts yet.',
          add:   'Add Account',
          notLoaded: 'Could not load accounts.',
        },
        settings: {
          title: 'Settings',
          sub:   'Appearance and preferences',
        },
        imports: {
          title:      'Import Center',
          sub:        'Bulk import from AO3 and other platforms',
          intro:      'Imports use connected accounts or extension-assisted syncing when available. Select an import type below to queue a job when support is ready.',
          history:    'Import history',
          noJobs:     'No imports have been run yet.',
          notLoaded:  'Could not load import history.',
          comingSoon: 'Coming soon',
        },
      },
      settings: {
        sections: {
          theme:     'Theme',
          privacy:   'Privacy',
          extension: 'Extension',
          display:   'Display',
          language:  'Language',
        },
        theme: {
          lightDark:   'Light / Dark',
          accent:      'Accent colour',
          density:     'Card density',
          densityHint: 'Comfortable shows more detail per card; Compact shows more cards',
          comfortable: 'Comfortable',
          compact:     'Compact',
          light:       'Light',
          dark:        'Dark',
        },
        privacy: {
          privacyMode:       'Privacy Mode',
          privacyModeHint:   'Vault always starts closed each session, regardless of last state',
          rememberVault:     'Remember vault state',
          rememberVaultHint: 'Restore open / closed state between sessions when Privacy Mode is off',
        },
        extension: {
          autoLog:       'Auto-log AO3 pages',
          autoLogHint:   'Automatically record reading history when you visit AO3',
          showToast:     'Show extension status',
          showToastHint: 'Display toast notifications when the extension logs a page',
        },
        display: {
          showKudos:      'Show kudos icon',
          showKudosHint:  'Show a heart icon on story cards where kudos was given',
          showChips:      'Show collection chips',
          showChipsHint:  'Show collection badges on story cards',
        },
        language: {
          label:     'Interface language',
          labelHint: 'Translates StoryVault interface only. Story content is never translated.',
        },
      },
      stats: {
        storiesSaved:      'Stories saved',
        wordsArchived:     'Words archived',
        kudosed:           'Kudosed',
        collections:       'Collections',
        connectedAccounts: 'Connected accounts',
        byStoryStatus:     'Works by official status',
        byReadingStatus:   'Works by reading status',
        topFandoms:        'Top fandoms',
        topAuthors:        'Top authors',
        topRelationships:  'Top ships & relationships',
        topTags:           'Top freeform tags',
        mostAccessed:      'Most accessed',
        recentlyAccessed:  'Recently accessed',
        noHistory:         'No reading history',
        noData:            'No data',
        notLoaded:         'Could not load statistics.',
      },
      detail: {
        originalUrl:    'Original URL',
        readingStatus:  'Reading status',
        currentChapter: 'Current chapter',
        lastAccessed:   'Last accessed',
        firstAccessed:  'First accessed',
        timesRead:      'Times read',
        rating:         'Rating',
        wordCount:      'Word count',
        chapters:       'Chapters',
        language:       'Language',
        ao3Published:   'AO3 published',
        ao3Updated:     'AO3 updated',
        completed:      'Completed',
        added:          'Added',
        lastUpdated:    'Last updated',
        kudos:          'Kudos',
        summary:        'Summary',
        categories:     'Categories',
        warnings:       'Warnings',
        ships:          'Ships',
        characters:     'Characters',
        tags:           'Tags',
        readingHistory: 'Reading history',
        noHistory:      'No reading history recorded.',
        continueBtn:    'Continue Reading',
        editBtn:        'Edit',
      },
      modal: {
        addStory:          'Add Story',
        editStory:         'Edit Story',
        manageCollections: 'Manage Collections',
        manageAccounts:    'Connected Accounts',
        newCollection:     'New Collection',
        renameCollection:  'Rename Collection',
      },
      import: {
        aoHistory:   { label: 'AO3 History',      desc: 'Import your complete AO3 reading history.' },
        aoBookmarks: { label: 'AO3 Bookmarks',     desc: 'Import all bookmarked works from your AO3 account.' },
        aoSubs:      { label: 'AO3 Subscriptions', desc: 'Import works and series you subscribe to on AO3.' },
        wattpad:     { label: 'Wattpad Library',   desc: 'Import your Wattpad reading library and following list.' },
        ffn:         { label: 'FFN Favorites',     desc: 'Import favorites and followed stories from FanFiction.net.' },
      },
    },
  };

  let _lang = 'en';

  function load() {
    const saved = localStorage.getItem(STORAGE_KEY) || 'en';
    _lang = (SUPPORTED_LANGS[saved] && SUPPORTED_LANGS[saved].available) ? saved : 'en';
    document.documentElement.lang = _lang;
  }

  function setLang(code) {
    if (!SUPPORTED_LANGS[code] || !SUPPORTED_LANGS[code].available) return;
    _lang = code;
    localStorage.setItem(STORAGE_KEY, code);
    document.documentElement.lang = _lang;
    apply();
  }

  function getLang() { return _lang; }

  function t(key) {
    const active   = STRINGS[_lang]  || STRINGS['en'];
    const fallback = STRINGS['en'];
    return dotGet(active, key) ?? dotGet(fallback, key) ?? key;
  }

  function dotGet(obj, key) {
    return key.split('.').reduce(
      (o, k) => (o != null && k in o ? o[k] : undefined),
      obj
    );
  }

  function apply() {
    document.querySelectorAll('[data-i18n]').forEach(el => {
      const key = el.dataset.i18n;
      const val = t(key);
      if (val !== key) el.textContent = val;
    });
    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {
      const key = el.dataset.i18nPlaceholder;
      const val = t(key);
      if (val !== key) el.placeholder = val;
    });
    document.querySelectorAll('[data-i18n-title]').forEach(el => {
      const key = el.dataset.i18nTitle;
      const val = t(key);
      if (val !== key) el.title = val;
    });
  }

  global.i18n = { t, load, setLang, getLang, apply, SUPPORTED_LANGS };
  global.t = t;

}(window));
