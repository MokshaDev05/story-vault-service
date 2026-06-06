'use strict';

const API = 'http://localhost:8080/api/v1';

let allStories = [];
let token = localStorage.getItem('sv_token');
let editingStoryId = null;
let advancedMode = false;      // true when showing POST /search results
let advPanelOpen = false;
let quickFilters = {};         // active field-specific filters: {author, fandom, tag, relationship}

// ─── Init ─────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  loadTheme();
  loadMetal();
  loadDensity();
  bindEvents();
  token ? boot() : showLogin();
});

function boot() {
  showApp();
  fetchStories();
}

// ─── Theme ────────────────────────────────────────────────────────────────────

function loadTheme() {
  document.documentElement.dataset.theme =
    localStorage.getItem('sv_theme') || 'light';
}

function toggleTheme() {
  const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
  document.documentElement.dataset.theme = next;
  localStorage.setItem('sv_theme', next);
}

// ─── Metal ────────────────────────────────────────────────────────────────────

function loadMetal() {
  setMetal(localStorage.getItem('sv_metal') || 'gold', false);
}

function setMetal(metal, save = true) {
  document.documentElement.dataset.metal = metal;
  if (save) localStorage.setItem('sv_metal', metal);
  document.querySelectorAll('.metal-btn').forEach(btn => {
    btn.classList.toggle('active', btn.dataset.metal === metal);
  });
}

// ─── Density ──────────────────────────────────────────────────────────────────

function loadDensity() {
  setDensity(localStorage.getItem('sv_density') || 'minimal', false);
}

function setDensity(density, save = true) {
  document.documentElement.dataset.density = density;
  if (save) localStorage.setItem('sv_density', density);
  const btn = el('density-btn');
  if (btn) btn.textContent = density === 'maximal' ? '◈ Minimal' : '◈ Maximal';
}

function toggleDensity() {
  const next = document.documentElement.dataset.density === 'maximal' ? 'minimal' : 'maximal';
  setDensity(next);
}

// ─── Header behaviour ─────────────────────────────────────────────────────────

function initCompactHeader() {
  const header    = document.querySelector('.site-header');
  const scrollBtn = el('scroll-to-controls');

  if (scrollBtn) {
    scrollBtn.addEventListener('click', () => {
      const controls = el('controls-bar');
      if (controls) controls.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }

  // Hysteresis: compact triggers at >120px, expands again at <60px.
  // The 60px dead-band prevents rapid toggling when scroll position
  // hovers near a single threshold.
  const COMPACT_AT = 120;
  const EXPAND_AT  = 60;
  let isCompact = false;
  let ticking   = false;

  window.addEventListener('scroll', () => {
    if (ticking) return;
    ticking = true;
    requestAnimationFrame(() => {
      const y = window.scrollY;
      if (!isCompact && y > COMPACT_AT) {
        isCompact = true;
        header.classList.add('header-compact');
      } else if (isCompact && y < EXPAND_AT) {
        isCompact = false;
        header.classList.remove('header-compact');
      }
      ticking = false;
    });
  }, { passive: true });
}

// ─── Auth ─────────────────────────────────────────────────────────────────────

function showLogin() {
  el('app').classList.add('hidden');
  el('login-section').classList.remove('hidden');
  showLoginMode();
}

function showApp() {
  el('login-section').classList.add('hidden');
  el('app').classList.remove('hidden');
  const username = usernameFromToken();
  const headerUser = el('header-username');
  if (headerUser) {
    headerUser.textContent = username;
    headerUser.classList.toggle('hidden', !username);
  }
}

function showLoginMode() {
  el('register-mode').classList.add('hidden');
  el('login-mode').classList.remove('hidden');
  el('login-tagline').textContent = 'Your private literary archive.';
  el('login-error').classList.add('hidden');
  setTimeout(() => el('login-username').focus(), 50);
}

function showRegisterMode() {
  el('login-mode').classList.add('hidden');
  el('register-mode').classList.remove('hidden');
  el('login-tagline').textContent = 'Create your account to begin.';
  el('register-error').classList.add('hidden');
  el('reg-username').value  = '';
  el('reg-password').value  = '';
  el('reg-confirm').value   = '';
  setTimeout(() => el('reg-username').focus(), 50);
}

async function login() {
  const username = el('login-username').value.trim();
  const password = el('login-password').value;
  const btn      = el('login-btn');
  const errEl    = el('login-error');

  errEl.classList.add('hidden');

  if (!username || !password) {
    errEl.textContent = 'Enter your username and password.';
    errEl.classList.remove('hidden');
    return;
  }

  btn.disabled    = true;
  btn.textContent = 'Opening vault…';

  try {
    const res  = await fetch(`${API}/auth/login`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, password }),
    });

    if (!res.ok) {
      errEl.textContent = 'Incorrect username or password.';
      errEl.classList.remove('hidden');
      btn.disabled    = false;
      btn.textContent = 'Enter the Vault';
      return;
    }

    const body = await res.json();
    token = body.data.token;
    localStorage.setItem('sv_token', token);
    boot();
  } catch {
    errEl.textContent = 'Could not reach StoryVault. Is the service running on localhost:8080?';
    errEl.classList.remove('hidden');
    btn.disabled    = false;
    btn.textContent = 'Enter the Vault';
  }
}

async function register() {
  const username = el('reg-username').value.trim();
  const password = el('reg-password').value;
  const confirm  = el('reg-confirm').value;
  const btn      = el('register-btn');
  const errEl    = el('register-error');

  errEl.classList.add('hidden');

  if (!username || !password || !confirm) {
    errEl.textContent = 'All fields are required.';
    errEl.classList.remove('hidden');
    return;
  }
  if (username.length < 3) {
    errEl.textContent = 'Username must be at least 3 characters.';
    errEl.classList.remove('hidden');
    return;
  }
  if (password.length < 6) {
    errEl.textContent = 'Password must be at least 6 characters.';
    errEl.classList.remove('hidden');
    return;
  }
  if (password !== confirm) {
    errEl.textContent = 'Passwords do not match.';
    errEl.classList.remove('hidden');
    return;
  }

  btn.disabled    = true;
  btn.textContent = 'Creating account…';

  try {
    const res  = await fetch(`${API}/auth/register`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({ username, password }),
    });

    const body = await res.json();

    if (res.status === 409) {
      errEl.textContent = 'That username is already taken.';
      errEl.classList.remove('hidden');
      btn.disabled    = false;
      btn.textContent = 'Create Account';
      return;
    }

    if (!res.ok) {
      const detail = body.message || `HTTP ${res.status}`;
      errEl.textContent = `Registration failed: ${detail}`;
      errEl.classList.remove('hidden');
      btn.disabled    = false;
      btn.textContent = 'Create Account';
      return;
    }

    token = body.data.token;
    localStorage.setItem('sv_token', token);
    boot();
  } catch {
    errEl.textContent = 'Could not reach StoryVault. Is the service running on localhost:8080?';
    errEl.classList.remove('hidden');
    btn.disabled    = false;
    btn.textContent = 'Create Account';
  }
}

function logout() {
  token = null;
  localStorage.removeItem('sv_token');
  allStories = [];
  quickFilters = {};
  el('cards-grid').innerHTML = '';
  const headerUser = el('header-username');
  if (headerUser) { headerUser.textContent = ''; headerUser.classList.add('hidden'); }
  showLogin();
}

function handleUnauthorized() {
  logout();
}

// ─── Data ─────────────────────────────────────────────────────────────────────

async function fetchStories() {
  el('loading-state').classList.remove('hidden');
  el('fetch-error').classList.add('hidden');
  el('empty-state').classList.add('hidden');
  el('story-count').classList.add('hidden');
  el('cards-grid').innerHTML = '';

  try {
    const res = await fetch(`${API}/stories`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    if (res.status === 401) { handleUnauthorized(); return; }

    const body = await res.json();
    allStories = body.data || [];
    el('loading-state').classList.add('hidden');
    applyFilters();
  } catch {
    el('loading-state').classList.add('hidden');
    el('fetch-error').classList.remove('hidden');
  }
}

async function createStory(data) {
  const res = await fetch(`${API}/stories`, {
    method:  'POST',
    headers: {
      'Content-Type':  'application/json',
      Authorization:   `Bearer ${token}`,
    },
    body: JSON.stringify(data),
  });

  if (res.status === 401) { handleUnauthorized(); throw new Error('Unauthorized'); }

  const body = await res.json();
  if (!res.ok) throw new Error(body.message || `HTTP ${res.status}`);
  return body.data;
}

async function updateStory(id, data) {
  const res = await fetch(`${API}/stories/${id}`, {
    method:  'PUT',
    headers: {
      'Content-Type':  'application/json',
      Authorization:   `Bearer ${token}`,
    },
    body: JSON.stringify(data),
  });

  if (res.status === 401) { handleUnauthorized(); throw new Error('Unauthorized'); }

  const body = await res.json();
  if (!res.ok) throw new Error(body.message || `HTTP ${res.status}`);
  return body.data;
}

async function deleteStory(id) {
  const res = await fetch(`${API}/stories/${id}`, {
    method:  'DELETE',
    headers: { Authorization: `Bearer ${token}` },
  });

  if (res.status === 401) { handleUnauthorized(); throw new Error('Unauthorized'); }
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || `HTTP ${res.status}`);
  }
}

async function fetchReadingHistory(storyId) {
  try {
    const res = await fetch(`${API}/stories/${storyId}/access`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return [];
    const body = await res.json();
    return body.data || [];
  } catch {
    return [];
  }
}

// ─── Filters ──────────────────────────────────────────────────────────────────

function applyFilters() {
  if (advancedMode) return;  // advanced search results are already rendered

  const search   = el('search-input').value.toLowerCase().trim();
  const platform = el('filter-platform').value;
  const status   = el('filter-status').value;

  let list = allStories;

  if (search) {
    list = list.filter(s =>
      s.title.toLowerCase().includes(search)  ||
      s.author.toLowerCase().includes(search) ||
      s.fandom.toLowerCase().includes(search) ||
      (s.tags          || []).some(t => t.toLowerCase().includes(search)) ||
      (s.relationships || []).some(r => r.toLowerCase().includes(search)) ||
      (s.characters    || []).some(c => c.toLowerCase().includes(search))
    );
  }
  if (platform) list = list.filter(s => s.platform === platform);
  if (status)   list = list.filter(s => s.status   === status);

  if (quickFilters.author)
    list = list.filter(s => s.author.toLowerCase() === quickFilters.author.toLowerCase());
  if (quickFilters.fandom)
    list = list.filter(s => s.fandom.toLowerCase() === quickFilters.fandom.toLowerCase());
  if (quickFilters.tag)
    list = list.filter(s => (s.tags || []).some(t => t.toLowerCase() === quickFilters.tag.toLowerCase()));
  if (quickFilters.relationship)
    list = list.filter(s => (s.relationships || []).some(r => r.toLowerCase() === quickFilters.relationship.toLowerCase()));

  renderCards(list);
  updateCount(list.length, false);
  renderQuickFilterChips();
}

function updateCount(shown, isAdvanced) {
  const countEl = el('story-count');
  if (isAdvanced) {
    countEl.textContent = `${shown} ${shown === 1 ? 'result' : 'results'} — advanced search`;
    countEl.classList.remove('hidden');
    return;
  }
  const total = allStories.length;
  if (total === 0) { countEl.classList.add('hidden'); return; }
  countEl.textContent = shown === total
    ? `${total} ${total === 1 ? 'story' : 'stories'} in your vault`
    : `Showing ${shown} of ${total} stories`;
  countEl.classList.remove('hidden');
}

// ─── Advanced search ──────────────────────────────────────────────────────────

function toggleAdvPanel() {
  advPanelOpen = !advPanelOpen;
  el('adv-panel').classList.toggle('hidden', !advPanelOpen);
  el('adv-toggle-btn').textContent = advPanelOpen ? '⊞ Advanced ▴' : '⊞ Advanced';
}

function buildAdvRequest() {
  const v    = id => el(id).value.trim();
  const num  = id => { const n = parseInt(el(id).value, 10); return isNaN(n) ? null : n; };
  const date = id => v(id) || null;

  return {
    titleContains:        v('search-input')       || null,
    fandomContains:       null,
    platform:             el('filter-platform').value || null,
    status:               el('filter-status').value   || null,
    rating:               v('adv-rating')            || null,
    readingStatus:        v('adv-reading-status')    || null,
    language:             v('adv-language')          || null,
    tagContains:          v('adv-tag')               || null,
    relationshipContains: v('adv-ship')              || null,
    characterContains:    v('adv-char')              || null,
    minWordCount:         num('adv-words-min'),
    maxWordCount:         num('adv-words-max'),
    minChapters:          num('adv-chaps-min'),
    maxChapters:          num('adv-chaps-max'),
    publishedAfter:       date('adv-pub-after'),
    publishedBefore:      date('adv-pub-before'),
    updatedAfter:         date('adv-upd-after'),
    updatedBefore:        date('adv-upd-before'),
    lastAccessedAfter:    date('adv-last-after'),
    lastAccessedBefore:   date('adv-last-before'),
    firstAccessedAfter:   date('adv-first-after'),
    firstAccessedBefore:  date('adv-first-before'),
    minAccessCount:       num('adv-min-reads'),
    chapterAccessed:      num('adv-chapter'),
    sortBy:               el('adv-sort-by').value  || 'LAST_ACCESSED',
    sortDir:              el('adv-sort-dir').value || 'desc',
  };
}

async function runAdvancedSearch() {
  const req = buildAdvRequest();

  el('loading-state').classList.remove('hidden');
  el('fetch-error').classList.add('hidden');
  el('empty-state').classList.add('hidden');
  el('cards-grid').innerHTML = '';

  try {
    const res = await fetch(`${API}/stories/search`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body:    JSON.stringify(req),
    });
    if (res.status === 401) { handleUnauthorized(); return; }

    const body = await res.json();
    const results = body.data || [];

    el('loading-state').classList.add('hidden');
    advancedMode = true;
    renderCards(results);
    updateCount(results.length, true);
    updateAdvChips(req);
  } catch {
    el('loading-state').classList.add('hidden');
    el('fetch-error').classList.remove('hidden');
  }
}

function clearAdvancedSearch() {
  // Reset all advanced inputs
  ['adv-last-after','adv-last-before','adv-first-after','adv-first-before',
   'adv-min-reads','adv-chapter','adv-language','adv-tag','adv-ship','adv-char',
   'adv-words-min','adv-words-max','adv-chaps-min','adv-chaps-max',
   'adv-pub-after','adv-pub-before','adv-upd-after','adv-upd-before'].forEach(id => {
    el(id).value = '';
  });
  el('adv-reading-status').value = '';
  el('adv-rating').value         = '';
  el('adv-sort-by').value        = 'LAST_ACCESSED';
  el('adv-sort-dir').value       = 'desc';
  el('adv-chips').innerHTML      = '';
  quickFilters = {};

  // Exit advanced mode and show the full library
  advancedMode = false;
  applyFilters();
}

// ─── Quick filters ────────────────────────────────────────────────────────────

function setQuickFilter(key, val) {
  quickFilters[key] = val;
  advancedMode = false;
  applyFilters();
  el('quick-filter-chips').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function clearQuickFilter(key) {
  delete quickFilters[key];
  applyFilters();
}

function clearAllQuickFilters() {
  quickFilters = {};
  applyFilters();
}

const QUICK_FILTER_LABELS = { author: 'Author', fandom: 'Fandom', tag: 'Tag', relationship: 'Ship' };

function renderQuickFilterChips() {
  const container = el('quick-filter-chips');
  if (!container) return;
  const entries = Object.entries(quickFilters);
  if (entries.length === 0) {
    container.classList.add('hidden');
    container.innerHTML = '';
    return;
  }
  container.innerHTML = entries.map(([key, val]) =>
    `<span class="quick-chip">
      <span class="quick-chip-label">${esc(QUICK_FILTER_LABELS[key] || key)}</span>
      ${esc(val)}
      <button class="quick-chip-remove" data-key="${escA(key)}" title="Remove filter">✕</button>
    </span>`
  ).join('') + `<button class="btn btn-ghost quick-chip-clear-all" id="quick-clear-all-btn">✕ Clear all</button>`;
  container.classList.remove('hidden');
}

function updateAdvChips(req) {
  const chips = [];
  const add = (label, val) => { if (val != null && val !== '') chips.push({ label, val: String(val) }); };

  add('Platform', req.platform);
  add('Status', req.status);
  add('Rating', req.rating ? RATING_LABELS[req.rating] : null);
  add('Reading', req.readingStatus ? READING_STATUS_LABELS[req.readingStatus] : null);
  add('Language', req.language);
  add('Tag', req.tagContains);
  add('Ship', req.relationshipContains);
  add('Character', req.characterContains);
  if (req.minWordCount != null || req.maxWordCount != null)
    chips.push({ label: 'Words', val: `${req.minWordCount ?? ''}–${req.maxWordCount ?? ''}` });
  if (req.minChapters != null || req.maxChapters != null)
    chips.push({ label: 'Chapters', val: `${req.minChapters ?? ''}–${req.maxChapters ?? ''}` });
  if (req.lastAccessedAfter  || req.lastAccessedBefore)
    chips.push({ label: 'Last read', val: `${req.lastAccessedAfter ?? ''}→${req.lastAccessedBefore ?? ''}` });
  if (req.firstAccessedAfter || req.firstAccessedBefore)
    chips.push({ label: 'First read', val: `${req.firstAccessedAfter ?? ''}→${req.firstAccessedBefore ?? ''}` });
  add('Min reads', req.minAccessCount);
  add('Chapter', req.chapterAccessed);
  if (req.publishedAfter || req.publishedBefore)
    chips.push({ label: 'Published', val: `${req.publishedAfter ?? ''}→${req.publishedBefore ?? ''}` });
  if (req.updatedAfter || req.updatedBefore)
    chips.push({ label: 'Updated', val: `${req.updatedAfter ?? ''}→${req.updatedBefore ?? ''}` });

  el('adv-chips').innerHTML = chips.map(c =>
    `<span class="adv-chip"><span class="adv-chip-label">${esc(c.label)}</span> ${esc(c.val)}</span>`
  ).join('');
}

// ─── Render cards ─────────────────────────────────────────────────────────────

function renderCards(stories) {
  const grid  = el('cards-grid');
  const empty = el('empty-state');

  if (stories.length === 0) {
    grid.innerHTML = '';
    empty.classList.remove('hidden');
    return;
  }

  empty.classList.add('hidden');
  grid.innerHTML = stories.map(cardHTML).join('');

  grid.querySelectorAll('.story-card').forEach(card => {
    const id   = Number(card.dataset.id);
    const open = () => { const s = allStories.find(x => x.id === id); if (s) openDetail(s); };
    card.addEventListener('click', open);
    card.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(); } });
  });

  grid.querySelectorAll('[data-filter-key]').forEach(btn => {
    btn.addEventListener('click', e => {
      e.stopPropagation();
      setQuickFilter(btn.dataset.filterKey, btn.dataset.filterVal);
    });
  });
}

function cardHTML(s) {
  const officialChip = `<span class="chip chip-${s.status.toLowerCase()}">${STATUS_LABELS[s.status] || s.status}</span>`;
  const badgeArea = s.readingStatus
    ? `<div class="badge-stack">
        <span class="chip chip-rs-${s.readingStatus.toLowerCase().replace(/_/g, '-')}">${READING_STATUS_LABELS[s.readingStatus] || s.readingStatus}</span>
        ${officialChip}
       </div>`
    : officialChip;

  const ships = (s.relationships || []).slice(0, 2);
  const shipHTML = ships.length
    ? `<p class="card-ships">${ships.map(r =>
        `<button class="card-ship-btn" data-filter-key="relationship" data-filter-val="${escA(r)}">${esc(r)}</button>`
      ).join(' · ')}</p>`
    : '';
  const tags = (s.tags || []).slice(0, 5).map(t =>
    `<button class="tag-pill tag-pill-btn" data-filter-key="tag" data-filter-val="${escA(t)}">${esc(t)}</button>`
  ).join('');
  const continueCardUrl = s.currentChapterUrl || s.originalUrl;
  const urlIcon = continueCardUrl
    ? `<a href="${escA(continueCardUrl)}" target="_blank" rel="noopener noreferrer" class="card-url-link" title="${s.currentChapterUrl ? 'Continue Reading' : 'Open original'}" onclick="event.stopPropagation()">↗</a>`
    : '';
  const fileIcon = s.hasFile ? `<span class="card-file-icon" title="File attached">◎</span>` : '';

  return `
    <article class="story-card" data-id="${s.id}" tabindex="0" role="button" aria-label="${escA(s.title)}">
      <span class="card-corner card-corner-tl" aria-hidden="true">◈</span>
      <span class="card-corner card-corner-tr" aria-hidden="true">◈</span>
      <span class="card-corner card-corner-bl" aria-hidden="true">◈</span>
      <span class="card-corner card-corner-br" aria-hidden="true">◈</span>
      <div class="card-top">
        <button class="card-fandom" data-filter-key="fandom" data-filter-val="${escA(s.fandom)}">${esc(s.fandom)}</button>
        ${badgeArea}
      </div>
      ${shipHTML}
      <h2 class="card-title">${esc(s.title)}</h2>
      <p class="card-author">by <button class="card-author-btn" data-filter-key="author" data-filter-val="${escA(s.author)}">${esc(s.author)}</button></p>
      <div class="card-rule"></div>
      <div class="card-meta">
        <span class="platform-badge">${PLATFORM_LABELS[s.platform] || s.platform}</span>
        <div class="card-tags">${tags}</div>
        <div class="card-icons">${fileIcon}${urlIcon}</div>
      </div>
    </article>`;
}

// ─── Detail modal ─────────────────────────────────────────────────────────────

function openDetail(s) {
  const officialChip = `<span class="chip chip-${s.status.toLowerCase()}">${STATUS_LABELS[s.status] || s.status}</span>`;
  const badgeArea = s.readingStatus
    ? `<div class="badge-stack">
        <span class="chip chip-rs-${s.readingStatus.toLowerCase().replace(/_/g, '-')}">${READING_STATUS_LABELS[s.readingStatus] || s.readingStatus}</span>
        ${officialChip}
       </div>`
    : officialChip;

  // Clickable tags filter on click
  const tagPills = (items, filterKey) => (items || []).map(t =>
    `<button class="tag-pill tag-pill-btn" data-filter-key="${filterKey}" data-filter-val="${escA(t)}" title="Filter by this">${esc(t)}</button>`
  ).join('');

  const tags     = tagPills(s.tags,          'tag');
  const ships    = tagPills(s.relationships,  'relationship');
  const chars    = tagPills(s.characters,     'character');
  const warnings = (s.archiveWarnings || []).map(w => `<span class="tag-pill tag-pill-warn">${esc(w)}</span>`).join('');
  const cats     = (s.categories     || []).map(c => `<span class="tag-pill tag-pill-cat">${esc(c)}</span>`).join('');

  const urlVal = s.originalUrl
    ? `<a href="${escA(s.originalUrl)}" target="_blank" rel="noopener noreferrer" class="detail-link">${esc(s.originalUrl)}</a>`
    : '<span style="color:var(--text-muted)">—</span>';

  const continueUrl = s.currentChapterUrl || s.originalUrl;

  const chapterDisplay = s.chapterCount != null
    ? (s.totalChapters != null ? `${s.chapterCount} / ${s.totalChapters}` : `${s.chapterCount}`)
    : '—';

  const metaRows = [
    ...(s.readingStatus ? [['Reading status', READING_STATUS_LABELS[s.readingStatus] || s.readingStatus]] : []),
    ...(s.currentChapter != null ? [['Current chapter', s.currentChapter]] : []),
    ...(s.lastAccessedAt  ? [['Last accessed',  fmtDate(s.lastAccessedAt)]]  : []),
    ...(s.firstAccessedAt ? [['First accessed', fmtDate(s.firstAccessedAt)]] : []),
    ...(s.accessCount != null ? [['Times read', s.accessCount]] : []),
    ['Rating',          RATING_LABELS[s.rating] || s.rating],
    ['Word count',      s.wordCount ? s.wordCount.toLocaleString() : '—'],
    ['Chapters',        chapterDisplay],
    ...(s.language         ? [['Language',    esc(s.language)]]                  : []),
    ...(s.ao3PublishedDate ? [['AO3 published', fmtDate(s.ao3PublishedDate)]]     : []),
    ...(s.ao3UpdatedDate   ? [['AO3 updated',   fmtDate(s.ao3UpdatedDate)]]       : []),
    ...(s.completedAt      ? [['Completed',     fmtDate(s.completedAt)]]          : []),
    ['Added',        fmtDate(s.createdAt)],
    ['Last updated', fmtDate(s.updatedAt)],
  ].map(([label, value]) => `
    <div class="detail-row">
      <span class="detail-label">${label}</span>
      <span class="detail-value">${String(value)}</span>
    </div>`).join('');

  const tagSections = [
    cats     ? `<div class="detail-tags-row"><span class="detail-label">Categories</span><div class="detail-tags">${cats}</div></div><hr class="modal-rule" />` : '',
    warnings ? `<div class="detail-tags-row"><span class="detail-label">Warnings</span><div class="detail-tags">${warnings}</div></div><hr class="modal-rule" />` : '',
    ships    ? `<div class="detail-tags-row"><span class="detail-label">Ships</span><div class="detail-tags">${ships}</div></div><hr class="modal-rule" />` : '',
    chars    ? `<div class="detail-tags-row"><span class="detail-label">Characters</span><div class="detail-tags">${chars}</div></div><hr class="modal-rule" />` : '',
    tags     ? `<div class="detail-tags-row"><span class="detail-label">Tags</span><div class="detail-tags">${tags}</div></div><hr class="modal-rule" />` : '',
  ].join('');

  el('detail-content').innerHTML = `
    <div class="detail-head">
      <div class="detail-fandom-row">
        <button class="card-fandom detail-fandom-btn tag-pill-btn" data-filter-key="fandom" data-filter-val="${escA(s.fandom)}">${esc(s.fandom)}</button>
        ${badgeArea}
      </div>
      <h2 class="detail-title">${esc(s.title)}</h2>
      <div class="detail-byline">
        <button class="detail-author tag-pill-btn" data-filter-key="author" data-filter-val="${escA(s.author)}">by ${esc(s.author)}</button>
        <span class="platform-badge">${PLATFORM_LABELS[s.platform] || s.platform}</span>
      </div>
    </div>
    ${s.summary ? `<hr class="modal-rule" /><p class="detail-summary">${esc(s.summary)}</p>` : ''}
    <hr class="modal-rule" />
    <div class="detail-row" style="padding: 12px 24px; border-bottom: none;">
      <span class="detail-label">Original URL</span>
      ${urlVal}
    </div>
    <hr class="modal-rule" />
    <div class="detail-meta">${metaRows}</div>
    <hr class="modal-rule" />
    ${tagSections}
    <div class="detail-actions">
      <div>
        ${continueUrl ? `<a href="${escA(continueUrl)}" target="_blank" rel="noopener noreferrer" class="btn btn-ghost" style="text-decoration:none;">↗ Continue Reading</a>` : ''}
      </div>
      <div class="detail-actions-right">
        <button class="btn btn-ghost" id="detail-edit-btn">Edit</button>
        <button class="btn btn-danger" id="detail-delete-btn">Delete</button>
      </div>
    </div>
    <div id="detail-history"></div>
  `;

  el('detail-modal').classList.remove('hidden');
  document.body.style.overflow = 'hidden';

  // Clickable tags/author/fandom — close modal and apply quick filter
  el('detail-content').querySelectorAll('.tag-pill-btn').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      closeDetail();
      setQuickFilter(btn.dataset.filterKey, btn.dataset.filterVal);
    });
  });

  el('detail-edit-btn').addEventListener('click', () => {
    closeDetail();
    openEditModal(s);
  });

  const deleteBtn = el('detail-delete-btn');
  let deleteConfirming = false;
  deleteBtn.addEventListener('click', async () => {
    if (!deleteConfirming) {
      deleteConfirming = true;
      deleteBtn.textContent = 'Confirm Delete';
      deleteBtn.classList.add('confirm-pending');
      return;
    }
    deleteBtn.disabled = true;
    deleteBtn.textContent = 'Deleting…';
    try {
      await deleteStory(s.id);
      allStories = allStories.filter(x => x.id !== s.id);
      closeDetail();
      applyFilters();
      updateCount(allStories.length);
    } catch (err) {
      deleteBtn.disabled = false;
      deleteBtn.textContent = 'Delete failed';
    }
  });

  loadReadingHistory(s.id);
}

async function loadReadingHistory(storyId) {
  const history = await fetchReadingHistory(storyId);
  const container = el('detail-history');
  if (!container) return;

  const body = history.length === 0
    ? `<p class="history-empty">No reading history recorded yet.</p>`
    : history.map(h => {
        const chapterDetail = h.chapterNumber ? `Ch. ${h.chapterNumber}` : '';
        const titleDetail   = h.chapterTitle  ? esc(h.chapterTitle)      : '';
        const detail = [chapterDetail, titleDetail].filter(Boolean).join(' — ') || 'Read';
        return `
          <div class="history-entry">
            <span class="history-date">${fmtDate(h.accessedAt)}</span>
            <span class="history-detail">${detail}</span>
          </div>`;
      }).join('');

  container.innerHTML = `
    <hr class="modal-rule" />
    <div class="history-section">
      <div class="history-label">Reading History</div>
      <div class="history-list">${body}</div>
    </div>`;
}

function closeDetail() {
  el('detail-modal').classList.add('hidden');
  document.body.style.overflow = '';
}

// ─── Add / Edit modal ─────────────────────────────────────────────────────────

function openAddModal() {
  editingStoryId = null;
  el('add-form').reset();
  el('add-form-error').classList.add('hidden');
  el('f-progress-section').classList.add('hidden');
  el('add-modal-title').textContent = 'Add to Vault';
  const btn = el('add-submit-btn');
  btn.disabled    = false;
  btn.textContent = 'Add to Vault';
  el('add-modal').classList.remove('hidden');
  document.body.style.overflow = 'hidden';
  setTimeout(() => el('f-title').focus(), 50);
}

function openEditModal(s) {
  editingStoryId = s.id;
  el('add-form').reset();
  el('add-form-error').classList.add('hidden');
  el('add-modal-title').textContent = 'Edit Story';

  el('f-title').value           = s.title   || '';
  el('f-author').value          = s.author  || '';
  el('f-fandom').value          = s.fandom  || '';
  el('f-platform').value        = s.platform || 'AO3';
  el('f-status').value          = s.status  || 'ONGOING';
  el('f-rating').value          = s.rating  || 'NOT_RATED';
  el('f-url').value             = s.originalUrl  || '';
  el('f-summary').value         = s.summary || '';
  el('f-words').value           = s.wordCount    != null ? s.wordCount    : '';
  el('f-chapters').value        = s.chapterCount != null ? s.chapterCount : '';
  el('f-tags').value            = (s.tags || []).join(', ');
  el('f-reading-status').value  = s.readingStatus || '';
  el('f-current-chapter').value = s.currentChapter != null ? s.currentChapter : '';
  el('f-current-chapter-url').value = s.currentChapterUrl || '';

  const rs = s.readingStatus;
  if (rs === 'STILL_READING' || rs === 'REREADING' || rs === 'CAUGHT_UP') {
    el('f-progress-section').classList.remove('hidden');
  } else {
    el('f-progress-section').classList.add('hidden');
  }

  const btn = el('add-submit-btn');
  btn.disabled    = false;
  btn.textContent = 'Save Changes';
  el('add-modal').classList.remove('hidden');
  document.body.style.overflow = 'hidden';
  setTimeout(() => el('f-title').focus(), 50);
}

function closeAddModal() {
  el('add-modal').classList.add('hidden');
  document.body.style.overflow = '';
  editingStoryId = null;
}

async function submitAdd(e) {
  e.preventDefault();

  const errEl = el('add-form-error');
  const btn   = el('add-submit-btn');
  errEl.classList.add('hidden');

  const title  = el('f-title').value.trim();
  const author = el('f-author').value.trim();
  const fandom = el('f-fandom').value.trim();

  if (!title || !author || !fandom) {
    errEl.textContent = 'Title, Author, and Fandom are required.';
    errEl.classList.remove('hidden');
    return;
  }

  const words    = el('f-words').value    ? Number(el('f-words').value)    : null;
  const chapters = el('f-chapters').value ? Number(el('f-chapters').value) : null;
  const tagInput = el('f-tags').value;
  const tags     = tagInput.split(',').map(t => t.trim()).filter(Boolean);
  const rs       = el('f-reading-status').value || null;
  const curCh    = el('f-current-chapter').value ? Number(el('f-current-chapter').value) : null;
  const curUrl   = el('f-current-chapter-url').value.trim() || null;

  const data = {
    title,
    author,
    fandom,
    platform:          el('f-platform').value,
    status:            el('f-status').value,
    rating:            el('f-rating').value,
    originalUrl:       el('f-url').value.trim()     || null,
    summary:           el('f-summary').value.trim() || null,
    wordCount:         words,
    chapterCount:      chapters,
    tags,
    readingStatus:     rs,
    currentChapter:    curCh,
    currentChapterUrl: curUrl,
  };

  btn.disabled    = true;
  btn.textContent = 'Saving…';

  try {
    if (editingStoryId) {
      await updateStory(editingStoryId, data);
    } else {
      await createStory(data);
    }
    closeAddModal();
    fetchStories();
  } catch (err) {
    errEl.textContent = err.message.toLowerCase().includes('already')
      ? 'This story is already in your vault.'
      : `Failed to save: ${err.message}`;
    errEl.classList.remove('hidden');
    btn.disabled    = false;
    btn.textContent = editingStoryId ? 'Save Changes' : 'Add to Vault';
  }
}

// ─── Events ───────────────────────────────────────────────────────────────────

function bindEvents() {
  el('login-btn').addEventListener('click', login);
  el('login-password').addEventListener('keydown', e => { if (e.key === 'Enter') login(); });

  el('show-register-btn').addEventListener('click', showRegisterMode);
  el('show-login-btn').addEventListener('click', showLoginMode);
  el('register-btn').addEventListener('click', register);
  el('reg-confirm').addEventListener('keydown', e => { if (e.key === 'Enter') register(); });

  el('logout-btn').addEventListener('click', logout);
  el('theme-btn').addEventListener('click', toggleTheme);
  el('density-btn').addEventListener('click', toggleDensity);
  document.querySelectorAll('.metal-btn').forEach(btn => {
    btn.addEventListener('click', () => setMetal(btn.dataset.metal));
  });
  el('refresh-btn').addEventListener('click', () => {
    advancedMode = false;
    quickFilters = {};
    el('adv-chips').innerHTML = '';
    renderQuickFilterChips();
    fetchStories();
  });

  el('quick-filter-chips').addEventListener('click', e => {
    const removeBtn = e.target.closest('.quick-chip-remove');
    if (removeBtn) { clearQuickFilter(removeBtn.dataset.key); return; }
    if (e.target.closest('#quick-clear-all-btn')) clearAllQuickFilters();
  });
  el('add-btn').addEventListener('click', openAddModal);
  el('empty-add-btn').addEventListener('click', openAddModal);

  el('search-input').addEventListener('input', applyFilters);
  el('filter-platform').addEventListener('change', applyFilters);
  el('filter-status').addEventListener('change', applyFilters);

  el('adv-toggle-btn').addEventListener('click', toggleAdvPanel);
  el('adv-search-btn').addEventListener('click', runAdvancedSearch);
  el('adv-clear-btn').addEventListener('click', clearAdvancedSearch);
  el('adv-search-btn').closest('form')?.addEventListener('submit', e => e.preventDefault());

  el('add-form').addEventListener('submit', submitAdd);
  el('add-modal-close').addEventListener('click', closeAddModal);
  el('add-cancel-btn').addEventListener('click', closeAddModal);
  el('add-modal').addEventListener('click', e => { if (e.target === e.currentTarget) closeAddModal(); });

  el('detail-close').addEventListener('click', closeDetail);
  el('detail-modal').addEventListener('click', e => { if (e.target === e.currentTarget) closeDetail(); });

  el('f-reading-status').addEventListener('change', () => {
    const rs = el('f-reading-status').value;
    el('f-progress-section').classList.toggle(
      'hidden',
      rs !== 'STILL_READING' && rs !== 'REREADING' && rs !== 'CAUGHT_UP'
    );
  });

  document.addEventListener('keydown', e => {
    if (e.key !== 'Escape') return;
    if (!el('detail-modal').classList.contains('hidden')) closeDetail();
    else if (!el('add-modal').classList.contains('hidden')) closeAddModal();
  });

  initCompactHeader();
}

// ─── Lookup tables ────────────────────────────────────────────────────────────

const STATUS_LABELS = {
  ONGOING: 'Ongoing', COMPLETE: 'Completed', HIATUS: 'Hiatus', ABANDONED: 'Abandoned',
};
const READING_STATUS_LABELS = {
  WANT_TO_READ:     'Want to Read',
  STILL_READING:    'Reading',
  CAUGHT_UP:        'Caught Up',
  FINISHED_READING: 'Finished',
  ON_HOLD:          'On Hold',
  DNF:              'DNF',
  REREADING:        'Rereading',
};
const RATING_LABELS = {
  NOT_RATED: 'Not rated', GENERAL: 'General', TEEN: 'Teen', MATURE: 'Mature', EXPLICIT: 'Explicit',
};
const PLATFORM_LABELS = {
  AO3: 'AO3', FFN: 'FFN', WATTPAD: 'Wattpad', OTHER: 'Other',
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

function el(id) { return document.getElementById(id); }

function usernameFromToken() {
  try { return JSON.parse(atob(token.split('.')[1])).sub || ''; } catch { return ''; }
}

function esc(s) {
  return String(s ?? '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function escA(s) { return String(s ?? '').replace(/"/g,'&quot;'); }

function fmtDate(iso) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleDateString('en-GB', { day:'numeric', month:'long', year:'numeric' });
  } catch { return iso; }
}
