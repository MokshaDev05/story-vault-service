'use strict';

// Fallback so any t() call is safe even if i18n.js failed to load
if (typeof window.t !== 'function') {
  window.t = key => key;
}
if (typeof window.i18n !== 'object' || !window.i18n) {
  window.i18n = { load: () => {}, apply: () => {}, setLang: () => {}, getLang: () => 'en', SUPPORTED_LANGS: {} };
}

const API = 'http://localhost:8080/api/v1';

let allStories = [];
let allCollections = [];
let allLabels = [];
let token = localStorage.getItem('sv_token');
let editingStoryId = null;
let advancedMode = false;      // true when showing POST /search results
let advPanelOpen = false;
let quickFilters = {};         // active field-specific filters: {author, fandom, tag, relationship}
let vaultOpen = false;
let privacyMode = false;
let dataLoaded = false;
let currentView = 'library';
let rememberVault = true;
let autoLog       = true;
let showToast     = true;
let showKudos     = true;
let showChips     = true;

// ─── Init ─────────────────────────────────────────────────────────────────────

document.addEventListener('DOMContentLoaded', () => {
  loadTheme();
  loadMetal();
  loadDensity();
  loadDisplayPrefs();
  bindEvents();
  try { i18n.load(); } catch (e) { console.warn('[StoryVault] i18n load failed:', e); }
  token ? boot() : showLogin();
});

function isTokenExpired(tok) {
  try {
    const b64 = tok.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - b64.length % 4) % 4);
    return JSON.parse(atob(padded)).exp * 1000 < Date.now();
  } catch { return true; }
}

function completeLogin(newToken) {
  token = newToken;
  localStorage.setItem('sv_vault_open', '1');
  boot();
}

function boot() {
  showApp();
  navigateTo('library');
  loadVaultState();
  try { i18n.apply(); } catch (e) { console.warn('i18n apply failed:', e); }
  if (vaultOpen) {
    dataLoaded = true;
    fetchStories();
    fetchCollections();
    fetchLabels();
    startImportWatcher();
  }
}

// ─── Live import watcher ──────────────────────────────────────────────────────

let _importWatcherTimer = null;

function startImportWatcher() {
  if (_importWatcherTimer) return;

  async function tick() {
    try {
      const res = await fetch(`${API}/imports`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!res.ok) { stopImportWatcher(); return; }
      const body = await res.json();
      const jobs = body.data || [];
      const running = jobs.find(j => j.status === 'RUNNING' || j.status === 'PENDING');
      if (!running) { stopImportWatcher(); return; }
      fetchStories();
    } catch { /* ignore */ }
  }

  tick();
  _importWatcherTimer = setInterval(tick, 8000);
}

function stopImportWatcher() {
  if (_importWatcherTimer) { clearInterval(_importWatcherTimer); _importWatcherTimer = null; }
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
  updateSettingsPage();
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
  updateSettingsPage();
}

function toggleDensity() {
  const next = document.documentElement.dataset.density === 'maximal' ? 'minimal' : 'maximal';
  setDensity(next);
}

function loadDisplayPrefs() {
  rememberVault = localStorage.getItem('sv_remember_vault') !== '0';
  autoLog       = localStorage.getItem('sv_auto_log')       !== '0';
  showToast     = localStorage.getItem('sv_show_toast')     !== '0';
  showKudos     = localStorage.getItem('sv_show_kudos')     !== '0';
  showChips     = localStorage.getItem('sv_show_chips')     !== '0';
}

function toggleRememberVault() {
  rememberVault = !rememberVault;
  localStorage.setItem('sv_remember_vault', rememberVault ? '1' : '0');
  updateSettingsPage();
}

function toggleAutoLog() {
  autoLog = !autoLog;
  localStorage.setItem('sv_auto_log', autoLog ? '1' : '0');
  updateSettingsPage();
}

function toggleShowToast() {
  showToast = !showToast;
  localStorage.setItem('sv_show_toast', showToast ? '1' : '0');
  updateSettingsPage();
}

function toggleShowKudos() {
  showKudos = !showKudos;
  localStorage.setItem('sv_show_kudos', showKudos ? '1' : '0');
  updateSettingsPage();
  if (currentView === 'library') applyFilters();
}

function toggleShowChips() {
  showChips = !showChips;
  localStorage.setItem('sv_show_chips', showChips ? '1' : '0');
  updateSettingsPage();
  if (currentView === 'library') applyFilters();
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
  const btn = el('login-btn');
  if (btn) { btn.disabled = false; btn.textContent = 'Enter the Vault'; }
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
  const btn = el('register-btn');
  if (btn) { btn.disabled = false; btn.textContent = 'Create Account'; }
  setTimeout(() => el('reg-username').focus(), 50);
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
  localStorage.removeItem('sv_vault_open');
  allStories = [];
  allCollections = [];
  quickFilters = {};
  vaultOpen = false;
  dataLoaded = false;
  currentView = 'library';
  closeNavDrawer();
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

async function fetchNotes(storyId) {
  try {
    const res = await fetch(`${API}/stories/${storyId}/notes`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return [];
    const body = await res.json();
    return body.data || [];
  } catch {
    return [];
  }
}

async function fetchCollections() {
  try {
    const res = await fetch(`${API}/collections`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return;
    const body = await res.json();
    allCollections = body.data || [];
    renderCollectionFilter();
    if (currentView === 'collections') renderCollectionsPage();
  } catch {}
}

async function fetchLabels() {
  try {
    const res = await fetch(`${API}/labels`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return;
    const body = await res.json();
    allLabels = body.data || [];
    renderLabelFilter();
  } catch {}
}

function renderLabelFilter() {
  const sel = el('filter-label');
  if (!sel) return;
  const current = sel.value;
  while (sel.options.length > 1) sel.remove(1);
  allLabels.forEach(lb => {
    const opt = document.createElement('option');
    opt.value = String(lb.id);
    opt.textContent = lb.name;
    sel.add(opt);
  });
  if (current) sel.value = current;
}

function renderCollectionFilter() {
  const sel = el('filter-collection');
  if (!sel) return;
  const current = sel.value;
  while (sel.options.length > 1) sel.remove(1);
  allCollections.forEach(c => {
    const opt = document.createElement('option');
    opt.value = String(c.id);
    opt.textContent = c.name;
    sel.add(opt);
  });
  if (current) sel.value = current;
}

// ─── Collections modal ────────────────────────────────────────────────────────

async function openCollectionsModal() {
  el('collections-modal').classList.remove('hidden');
  document.body.style.overflow = 'hidden';
  resetCollectionForm();
  await renderCollectionsList();
}

function closeCollectionsModal() {
  el('collections-modal').classList.add('hidden');
  document.body.style.overflow = '';
}

async function renderCollectionsList() {
  const container = el('collections-list');
  container.innerHTML = `<p class="loading-state" style="padding:12px 0;">${t('ui.loading')}</p>`;
  try {
    const res = await fetch(`${API}/collections`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.status === 401) { handleUnauthorized(); return; }
    const body = await res.json();
    const collections = body.data || [];
    if (collections.length === 0) {
      container.innerHTML = '<p class="history-empty" style="padding:8px 0 16px;">No collections yet.</p>';
      return;
    }
    container.innerHTML = collections.map(c => `
      <div class="account-row" data-id="${c.id}">
        <div class="account-row-info">
          <span class="account-display-name">${esc(c.name)}</span>
          <span class="account-label-chip">${c.storyCount} ${c.storyCount === 1 ? 'story' : 'stories'}</span>
        </div>
        <div class="account-row-actions">
          <button class="btn btn-ghost btn-sm coll-edit-btn" data-id="${c.id}" data-name="${escA(c.name)}">Rename</button>
          <button class="btn btn-danger btn-sm coll-delete-btn" data-id="${c.id}">Delete</button>
        </div>
      </div>`).join('');

    container.querySelectorAll('.coll-edit-btn').forEach(btn => {
      btn.addEventListener('click', () => startRenameCollection(btn.dataset.id, btn.dataset.name));
    });
    container.querySelectorAll('.coll-delete-btn').forEach(btn => {
      btn.addEventListener('click', () => confirmDeleteCollectionFromList(btn.dataset.id, btn));
    });
  } catch {
    container.innerHTML = '<p class="fetch-error" style="padding:8px 0;">Could not load collections.</p>';
  }
}

function resetCollectionForm() {
  el('collection-form').reset();
  el('collection-editing-id').value = '';
  el('collection-form-title').textContent = 'New Collection';
  el('collection-submit-btn').textContent = 'Create';
  el('collection-submit-btn').disabled = false;
  el('collection-form-error').classList.add('hidden');
}

function startRenameCollection(id, name) {
  el('collection-editing-id').value = id;
  el('coll-name').value = name;
  el('collection-form-title').textContent = 'Rename Collection';
  el('collection-submit-btn').textContent = 'Save';
  el('collection-submit-btn').disabled = false;
  el('collection-form-error').classList.add('hidden');
  el('collection-form').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

async function submitCollectionForm(e) {
  e.preventDefault();
  const errEl  = el('collection-form-error');
  const btn    = el('collection-submit-btn');
  const editId = el('collection-editing-id').value;
  const name   = el('coll-name').value.trim();

  errEl.classList.add('hidden');

  if (!name) {
    errEl.textContent = 'Collection name is required.';
    errEl.classList.remove('hidden');
    return;
  }

  btn.disabled    = true;
  btn.textContent = 'Saving…';

  try {
    const url    = editId ? `${API}/collections/${editId}` : `${API}/collections`;
    const method = editId ? 'PUT' : 'POST';
    const res = await fetch(url, {
      method,
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify({ name }),
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      throw new Error(body.message || `HTTP ${res.status}`);
    }
    resetCollectionForm();
    await fetchCollections();
    await renderCollectionsList();
  } catch (err) {
    errEl.textContent = `Failed to save: ${err.message}`;
    errEl.classList.remove('hidden');
    btn.disabled    = false;
    btn.textContent = editId ? 'Save' : 'Create';
  }
}

async function confirmDeleteCollectionFromList(id, btn) {
  if (!btn.dataset.confirming) {
    btn.dataset.confirming = '1';
    btn.textContent = 'Confirm?';
    return;
  }
  btn.disabled    = true;
  btn.textContent = 'Deleting…';
  try {
    const res = await fetch(`${API}/collections/${id}`, {
      method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    await fetchCollections();
    await renderCollectionsList();
  } catch {
    btn.disabled    = false;
    btn.textContent = 'Delete failed';
  }
}

// ─── Filters ──────────────────────────────────────────────────────────────────

function applyFilters() {
  if (advancedMode) return;  // advanced search results are already rendered

  const search      = el('search-input').value.toLowerCase().trim();
  const platform    = el('filter-platform').value;
  const status      = el('filter-status').value;
  const kudos       = el('filter-kudos').value;
  const collFilter  = el('filter-collection').value;
  const labelFilter = el('filter-label') ? el('filter-label').value : '';

  let list = allStories;

  if (search) {
    list = list.filter(s =>
      s.title.toLowerCase().includes(search)  ||
      s.author.toLowerCase().includes(search) ||
      s.fandom.toLowerCase().includes(search) ||
      (s.tags          || []).some(t => t.toLowerCase().includes(search)) ||
      (s.relationships || []).some(r => r.toLowerCase().includes(search)) ||
      (s.characters    || []).some(c => c.toLowerCase().includes(search)) ||
      (s.personalNotes || '').toLowerCase().includes(search) ||
      (s.labels        || []).some(l => l.name.toLowerCase().includes(search))
    );
  }
  if (platform) list = list.filter(s => s.platform === platform);
  if (status)   list = list.filter(s => s.status   === status);
  if (kudos === 'GIVEN')     list = list.filter(s => s.kudosStatus === 'GIVEN');
  if (kudos === 'NOT_GIVEN') list = list.filter(s => s.kudosStatus !== 'GIVEN');
  if (collFilter) list = list.filter(s =>
    (s.collections || []).some(c => String(c.id) === collFilter));
  if (labelFilter) list = list.filter(s =>
    (s.labels || []).some(l => String(l.id) === labelFilter));

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
  add('Rating', req.rating ? t('rating.' + req.rating) : null);
  add('Reading', req.readingStatus ? t('readingStatus.' + req.readingStatus) : null);
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
  const officialChip = `<span class="chip chip-${s.status.toLowerCase()}">${t('status.' + s.status) || s.status}</span>`;
  const badgeArea = s.readingStatus
    ? `<div class="badge-stack">
        <span class="chip chip-rs-${s.readingStatus.toLowerCase().replace(/_/g, '-')}">${t('readingStatus.' + s.readingStatus) || s.readingStatus}</span>
        ${officialChip}
       </div>`
    : officialChip;

  const ships = (s.relationships || []).slice(0, 2);
  const shipHTML = ships.length
    ? `<p class="card-ships">${ships.map(r =>
        `<button class="card-ship-btn" data-filter-key="relationship" data-filter-val="${escA(r)}">${esc(r)}</button>`
      ).join(' · ')}</p>`
    : '';
  const tags = (s.tags || []).slice(0, 5).map(tagName =>
    `<button class="tag-pill tag-pill-btn" data-filter-key="tag" data-filter-val="${escA(tagName)}">${esc(tagName)}</button>`
  ).join('');
  const continueCardUrl = s.currentChapterUrl || s.originalUrl;
  const urlIcon = continueCardUrl
    ? `<a href="${escA(continueCardUrl)}" target="_blank" rel="noopener noreferrer" class="card-url-link" title="${s.currentChapterUrl ? 'Continue Reading' : 'Open original'}" onclick="event.stopPropagation()">↗</a>`
    : '';
  const fileIcon  = s.hasFile ? `<span class="card-file-icon" title="File attached">◎</span>` : '';
  const kudosIcon = showKudos && s.kudosStatus === 'GIVEN' ? `<span class="card-kudos-icon" title="Kudosed">♥</span>` : '';
  const collChips = showChips ? (s.collections || []).slice(0, 3).map(c =>
    `<span class="collection-chip collection-chip-card">${esc(c.name)}</span>`).join('') : '';
  const labelChips = (s.labels || []).slice(0, 3).map(l =>
    `<span class="label-chip label-chip-card">${esc(l.name)}</span>`).join('');
  const bottomRow = (collChips || labelChips)
    ? `<div class="card-collections">${collChips}${labelChips}</div>` : '';

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
      ${bottomRow}
      <div class="card-meta">
        <span class="platform-badge">${t('platform.' + s.platform) || s.platform}</span>
        <div class="card-tags">${tags}</div>
        <div class="card-icons">${fileIcon}${kudosIcon}${urlIcon}</div>
      </div>
    </article>`;
}

// ─── Detail modal ─────────────────────────────────────────────────────────────

function openDetail(s) {
  const officialChip = `<span class="chip chip-${s.status.toLowerCase()}">${t('status.' + s.status) || s.status}</span>`;
  const badgeArea = s.readingStatus
    ? `<div class="badge-stack">
        <span class="chip chip-rs-${s.readingStatus.toLowerCase().replace(/_/g, '-')}">${t('readingStatus.' + s.readingStatus) || s.readingStatus}</span>
        ${officialChip}
       </div>`
    : officialChip;

  // Clickable tags filter on click
  const tagPills = (items, filterKey) => (items || []).map(item =>
    `<button class="tag-pill tag-pill-btn" data-filter-key="${filterKey}" data-filter-val="${escA(item)}" title="Filter by this">${esc(item)}</button>`
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
    ...(s.readingStatus ? [['Reading status', t('readingStatus.' + s.readingStatus) || s.readingStatus]] : []),
    ...(s.currentChapter != null ? [['Current chapter', s.currentChapter]] : []),
    ...(s.lastAccessedAt  ? [['Last accessed',  fmtDate(s.lastAccessedAt)]]  : []),
    ...(s.firstAccessedAt ? [['First accessed', fmtDate(s.firstAccessedAt)]] : []),
    ...(s.accessCount != null ? [['Times read', s.accessCount]] : []),
    ['Rating',          t('rating.' + s.rating) || s.rating],
    ['Word count',      s.wordCount ? s.wordCount.toLocaleString() : '—'],
    ['Chapters',        chapterDisplay],
    ...(s.language         ? [['Language',    esc(s.language)]]                  : []),
    ...(s.ao3PublishedDate ? [['AO3 published', fmtDate(s.ao3PublishedDate)]]     : []),
    ...(s.ao3UpdatedDate   ? [['AO3 updated',   fmtDate(s.ao3UpdatedDate)]]       : []),
    ...(s.completedAt      ? [['Completed',     fmtDate(s.completedAt)]]          : []),
    ['Added',        fmtDate(s.createdAt)],
    ['Last updated', fmtDate(s.updatedAt)],
    ...(s.kudosStatus && s.kudosStatus !== 'UNKNOWN'
        ? [['Kudos', t('kudos.' + s.kudosStatus) || s.kudosStatus]] : []),
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
        <span class="platform-badge">${t('platform.' + s.platform) || s.platform}</span>
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
    <div id="detail-personal-note"></div>
    <div id="detail-labels"></div>
    <div id="detail-collections"></div>
    <div id="detail-notes"></div>
    <div id="detail-history"></div>
    <div id="detail-downloads"></div>
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

  loadPersonalNote(s.id, s.personalNotes);
  loadDetailLabels(s.id, s.labels || []);
  loadDetailCollections(s.id, s.collections || []);
  loadReadingHistory(s.id);
  loadNotes(s.id);
  loadDetailDownloads(s.id, s.originalUrl);
}

const HISTORY_MODE_LABELS = { CHAPTER: 'chapter', WORK_MAIN: 'main', FULL_WORK: 'full work' };

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
        const modeLabel = h.readingMode ? (HISTORY_MODE_LABELS[h.readingMode] || h.readingMode.toLowerCase()) : '';
        const modeBadge = modeLabel ? `<span class="history-mode">${modeLabel}</span>` : '';
        const linkIcon  = h.chapterUrl
          ? `<a class="history-link" href="${escA(h.chapterUrl)}" target="_blank" rel="noopener noreferrer" title="Open this chapter">↗</a>`
          : '';
        return `
          <div class="history-entry">
            <span class="history-date">${fmtDate(h.accessedAt)}</span>
            <span class="history-detail">${detail}</span>
            ${modeBadge}
            ${linkIcon}
          </div>`;
      }).join('');

  container.innerHTML = `
    <hr class="modal-rule" />
    <div class="history-section">
      <div class="history-label">Reading History</div>
      <div class="history-list">${body}</div>
    </div>`;
}

async function loadNotes(storyId) {
  const notes = await fetchNotes(storyId);
  const container = el('detail-notes');
  if (!container || notes.length === 0) return;
  const body = notes.map(n => `
    <div class="note-entry">
      <p class="note-content">${esc(n.content)}</p>
      <span class="note-date">${fmtDate(n.createdAt)}</span>
    </div>`).join('');
  container.innerHTML = `
    <hr class="modal-rule" />
    <div class="history-section">
      <div class="history-label">Notes</div>
      <div class="notes-list">${body}</div>
    </div>`;
}

async function loadDetailDownloads(storyId, storyOriginalUrl) {
  const container = el('detail-downloads');
  if (!container) return;

  const render = async () => {
    let records = [];
    try {
      const res = await fetch(`${API}/stories/${storyId}/downloads`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.status === 401) { handleUnauthorized(); return; }
      const body = await res.json();
      records = body.data || [];
    } catch { records = []; }

    const FILE_FORMATS = ['PDF', 'EPUB', 'HTML', 'MOBI', 'AZW3', 'OTHER'];
    const PLATFORMS_DL = ['AO3', 'WATTPAD', 'FFN', 'CUSTOM'];

    const recordRows = records.length === 0
      ? '<p style="color:var(--text-muted);font-size:12px;margin:0;">No downloads recorded yet.</p>'
      : records.map(r => {
          const dlUrl = r.sourceUrl || storyOriginalUrl;
          const dlLink = dlUrl
            ? `<a class="dl-again-link" href="${escA(dlUrl)}" target="_blank" rel="noopener noreferrer" title="Download Again">↗ Download Again</a>`
            : '';
          return `
            <div class="detail-dl-row" data-dl-id="${r.id}">
              <div class="dl-meta">
                <span class="dl-chips">
                  ${r.fileType ? `<span class="dl-chip">${esc(r.fileType)}</span>` : ''}
                  ${r.platform ? `<span class="dl-chip dl-chip-platform">${esc(r.platform)}</span>` : ''}
                </span>
                ${r.fileName ? `<span class="dl-filename">${esc(r.fileName)}</span>` : ''}
              </div>
              <div class="dl-row-right">
                <span class="dl-date">${r.downloadedAt ? r.downloadedAt.slice(0, 10) : ''}</span>
                ${dlLink}
                <button class="btn-icon dl-delete-btn" data-dl-id="${r.id}" title="Delete record">✕</button>
              </div>
            </div>`;
        }).join('');

    container.innerHTML = `
      <hr class="modal-rule" />
      <div class="history-section">
        <div class="history-label-row">
          <span class="history-label">Download History</span>
          <button class="btn btn-ghost btn-sm" id="detail-dl-record-btn">+ Record Download</button>
        </div>
        <div id="detail-dl-list">${recordRows}</div>
        <div id="detail-dl-form" class="detail-dl-form hidden">
          <select class="filter-select detail-dl-input" id="dl-f-format">
            <option value="">Format…</option>
            ${FILE_FORMATS.map(f => `<option value="${f}">${f}</option>`).join('')}
          </select>
          <select class="filter-select detail-dl-input" id="dl-f-platform">
            <option value="">Platform…</option>
            ${PLATFORMS_DL.map(p => `<option value="${p}">${p}</option>`).join('')}
          </select>
          <input class="detail-dl-input" type="text" id="dl-f-url" placeholder="Source URL (optional)" />
          <input class="detail-dl-input" type="text" id="dl-f-filename" placeholder="File name (optional)" />
          <input class="detail-dl-input" type="text" id="dl-f-notes" placeholder="Notes (optional)" />
          <div style="display:flex;gap:6px;margin-top:4px;">
            <button class="btn btn-ghost btn-sm" id="dl-f-save-btn">Save</button>
            <button class="btn btn-ghost btn-sm" id="dl-f-cancel-btn">Cancel</button>
            <span id="dl-f-status" style="font-size:11px;color:var(--text-muted);display:none;"></span>
          </div>
        </div>
      </div>`;

    el('detail-dl-record-btn').addEventListener('click', () => {
      el('detail-dl-form').classList.toggle('hidden');
    });

    el('detail-dl-cancel-btn').addEventListener('click', () => {
      el('detail-dl-form').classList.add('hidden');
    });

    el('dl-f-save-btn').addEventListener('click', async () => {
      const format   = el('dl-f-format').value   || null;
      const platform = el('dl-f-platform').value || null;
      const sourceUrl = el('dl-f-url').value.trim()      || null;
      const fileName  = el('dl-f-filename').value.trim() || null;
      const notes     = el('dl-f-notes').value.trim()    || null;
      const saveBtn   = el('dl-f-save-btn');
      const status    = el('dl-f-status');
      saveBtn.disabled = true;
      try {
        const res = await fetch(`${API}/stories/${storyId}/downloads`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ fileType: format, platform, sourceUrl, fileName, notes }),
        });
        if (res.status === 401) { handleUnauthorized(); return; }
        if (!res.ok) throw new Error();
        el('detail-dl-form').classList.add('hidden');
        await render();
      } catch {
        status.textContent = 'Error saving';
        status.style.display = 'inline';
      } finally {
        saveBtn.disabled = false;
      }
    });

    container.querySelectorAll('.dl-delete-btn').forEach(btn => {
      btn.addEventListener('click', async () => {
        const dlId = btn.dataset.dlId;
        await fetch(`${API}/stories/${storyId}/downloads/${dlId}`, {
          method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
        });
        await render();
      });
    });
  };

  await render();
}

function loadPersonalNote(storyId, currentNote) {
  const container = el('detail-personal-note');
  if (!container) return;

  const render = (note) => {
    container.innerHTML = `
      <hr class="modal-rule" />
      <div class="history-section">
        <div class="history-label">My Note</div>
        <textarea class="personal-note-area" id="detail-note-text" rows="3" placeholder="Add a personal note…">${esc(note || '')}</textarea>
        <div style="margin-top:6px;display:flex;gap:8px;align-items:center;">
          <button class="btn btn-ghost btn-sm" id="detail-note-save-btn">Save note</button>
          <span class="history-meta" id="detail-note-status" style="display:none;"></span>
        </div>
      </div>`;

    el('detail-note-save-btn').addEventListener('click', async () => {
      const content = el('detail-note-text').value.trim() || null;
      const saveBtn = el('detail-note-save-btn');
      const status  = el('detail-note-status');
      saveBtn.disabled = true;
      try {
        const res = await fetch(`${API}/stories/${storyId}/note`, {
          method: 'PUT',
          headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({ content }),
        });
        if (res.status === 401) { handleUnauthorized(); return; }
        const body = await res.json();
        const updated = body.data;
        const idx = allStories.findIndex(x => x.id === storyId);
        if (idx !== -1) allStories[idx].personalNotes = updated.personalNotes;
        status.textContent = 'Saved';
        status.style.display = 'inline';
        setTimeout(() => { status.style.display = 'none'; }, 2000);
      } catch {
        status.textContent = 'Error saving';
        status.style.display = 'inline';
      } finally {
        saveBtn.disabled = false;
      }
    });
  };

  render(currentNote);
}

function loadDetailLabels(storyId, currentLabels) {
  const container = el('detail-labels');
  if (!container) return;

  const render = (labels) => {
    const currentChips = labels.length === 0
      ? '<span style="color:var(--text-muted);font-size:13px;">No labels.</span>'
      : labels.map(l => `
          <span class="label-chip">
            ${esc(l.name)}
            <button class="label-chip-remove" data-label-id="${l.id}" data-story-id="${storyId}" title="Remove ${escA(l.name)}">✕</button>
          </span>`).join('');

    const available = allLabels.filter(al => !labels.some(l => l.id === al.id));
    const addOptions = available.length === 0 ? '' : `
      <div class="collections-add-row">
        <select class="filter-select collections-add-sel" id="detail-label-add-sel">
          <option value="">Add label…</option>
          ${available.map(l => `<option value="${l.id}">${esc(l.name)}</option>`).join('')}
        </select>
        <button class="btn btn-ghost btn-sm" id="detail-label-add-btn">Add</button>
      </div>`;

    container.innerHTML = `
      <hr class="modal-rule" />
      <div class="history-section">
        <div class="history-label">My Labels</div>
        <div class="detail-labels-chips">${currentChips}</div>
        ${addOptions}
      </div>`;

    container.querySelectorAll('.label-chip-remove').forEach(btn => {
      btn.addEventListener('click', async () => {
        const labelId = btn.dataset.labelId;
        btn.disabled = true;
        try {
          const res = await fetch(`${API}/labels/${labelId}/stories/${storyId}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${token}` },
          });
          if (res.status === 401) { handleUnauthorized(); return; }
          const next = labels.filter(l => String(l.id) !== labelId);
          const idx = allStories.findIndex(x => x.id === storyId);
          if (idx !== -1) allStories[idx].labels = next;
          render(next);
        } catch { btn.disabled = false; }
      });
    });

    const addBtn = el('detail-label-add-btn');
    if (addBtn) {
      addBtn.addEventListener('click', async () => {
        const sel = el('detail-label-add-sel');
        const labelId = sel?.value;
        if (!labelId) return;
        addBtn.disabled = true;
        try {
          const res = await fetch(`${API}/labels/${labelId}/stories/${storyId}`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${token}` },
          });
          if (res.status === 401) { handleUnauthorized(); return; }
          const added = allLabels.find(l => String(l.id) === labelId);
          if (added) {
            const next = [...labels, { id: added.id, name: added.name }];
            const idx = allStories.findIndex(x => x.id === storyId);
            if (idx !== -1) allStories[idx].labels = next;
            render(next);
          }
        } catch { addBtn.disabled = false; }
      });
    }
  };

  render(currentLabels);
}

function loadDetailCollections(storyId, currentCollections) {
  const container = el('detail-collections');
  if (!container) return;

  const render = (cols) => {
    const currentChips = cols.length === 0
      ? '<span style="color:var(--text-muted);font-size:13px;">Not in any collection.</span>'
      : cols.map(c => `
          <span class="collection-chip">
            ${esc(c.name)}
            <button class="collection-chip-remove" data-coll-id="${c.id}" data-story-id="${storyId}" title="Remove from ${escA(c.name)}">✕</button>
          </span>`).join('');

    const available = allCollections.filter(ac => !cols.some(c => c.id === ac.id));
    const addOptions = available.length === 0 ? '' : `
      <div class="collections-add-row">
        <select class="filter-select collections-add-sel" id="detail-coll-add-sel">
          <option value="">Add to collection…</option>
          ${available.map(c => `<option value="${c.id}">${esc(c.name)}</option>`).join('')}
        </select>
        <button class="btn btn-ghost btn-sm" id="detail-coll-add-btn">Add</button>
      </div>`;

    container.innerHTML = `
      <hr class="modal-rule" />
      <div class="history-section">
        <div class="history-label">Collections</div>
        <div class="detail-collections-chips">${currentChips}</div>
        ${addOptions}
      </div>`;

    container.querySelectorAll('.collection-chip-remove').forEach(btn => {
      btn.addEventListener('click', async () => {
        const collId = btn.dataset.collId;
        await fetch(`${API}/collections/${collId}/stories/${storyId}`, {
          method: 'DELETE', headers: { Authorization: `Bearer ${token}` },
        });
        const updated = cols.filter(c => String(c.id) !== collId);
        patchStoryCollections(storyId, updated);
        render(updated);
      });
    });

    const addBtn = el('detail-coll-add-btn');
    if (addBtn) {
      addBtn.addEventListener('click', async () => {
        const sel = el('detail-coll-add-sel');
        const collId = sel.value;
        if (!collId) return;
        const res = await fetch(`${API}/collections/${collId}/stories/${storyId}`, {
          method: 'POST', headers: { Authorization: `Bearer ${token}` },
        });
        if (res.ok) {
          const newColl = allCollections.find(c => String(c.id) === collId);
          if (newColl) {
            const updated = [...cols, { id: newColl.id, name: newColl.name }];
            updated.sort((a, b) => a.name.localeCompare(b.name));
            patchStoryCollections(storyId, updated);
            render(updated);
          }
        }
      });
    }
  };

  render(currentCollections);
}

function patchStoryCollections(storyId, collections) {
  const idx = allStories.findIndex(s => s.id === storyId);
  if (idx !== -1) allStories[idx] = { ...allStories[idx], collections };
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
  const tags     = tagInput.split(',').map(tag => tag.trim()).filter(Boolean);
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
  el('show-register-btn').addEventListener('click', showRegisterMode);
  el('show-login-btn').addEventListener('click', showLoginMode);
  el('register-btn').addEventListener('click', register);
  el('reg-confirm').addEventListener('keydown', e => { if (e.key === 'Enter') register(); });

  el('vault-toggle-btn').addEventListener('click', () => vaultOpen ? closeVault() : openVault());
  el('open-vault-btn').addEventListener('click', openVault);
  el('menu-btn').addEventListener('click', openNavDrawer);
  el('nav-close-btn').addEventListener('click', closeNavDrawer);
  el('nav-overlay').addEventListener('click', closeNavDrawer);
  el('nav-logout-btn').addEventListener('click', logout);
  el('nav-privacy-btn').addEventListener('click', togglePrivacyMode);
  el('page-manage-collections-btn').addEventListener('click', openCollectionsModal);
  el('page-add-account-btn').addEventListener('click', openAccountsModal);
  el('settings-theme-btn').addEventListener('click', toggleTheme);
  el('settings-density-btn').addEventListener('click', toggleDensity);
  el('settings-privacy-btn').addEventListener('click', togglePrivacyMode);
  el('settings-remember-vault-btn').addEventListener('click', toggleRememberVault);
  el('settings-auto-log-btn').addEventListener('click', toggleAutoLog);
  el('settings-show-toast-btn').addEventListener('click', toggleShowToast);
  el('settings-show-kudos-btn').addEventListener('click', toggleShowKudos);
  el('settings-show-chips-btn').addEventListener('click', toggleShowChips);
  document.querySelectorAll('.nav-item[data-view]').forEach(btn => {
    btn.addEventListener('click', () => navigateTo(btn.dataset.view));
  });
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
  el('filter-kudos').addEventListener('change', applyFilters);

  el('adv-toggle-btn').addEventListener('click', toggleAdvPanel);
  el('adv-search-btn').addEventListener('click', runAdvancedSearch);
  el('adv-clear-btn').addEventListener('click', clearAdvancedSearch);
  el('adv-search-btn').closest('form')?.addEventListener('submit', e => e.preventDefault());

  el('add-form').addEventListener('submit', submitAdd);
  el('add-modal-close').addEventListener('click', closeAddModal);
  el('add-cancel-btn').addEventListener('click', closeAddModal);
  el('add-modal').addEventListener('click', e => { if (e.target === e.currentTarget) closeAddModal(); });

  el('collections-modal-close').addEventListener('click', closeCollectionsModal);
  el('collections-modal').addEventListener('click', e => { if (e.target === e.currentTarget) closeCollectionsModal(); });
  el('collection-form').addEventListener('submit', submitCollectionForm);
  el('collection-cancel-btn').addEventListener('click', resetCollectionForm);
  el('filter-collection').addEventListener('change', applyFilters);
  el('filter-label').addEventListener('change', applyFilters);

  el('accounts-modal-close').addEventListener('click', closeAccountsModal);
  el('accounts-modal').addEventListener('click', e => { if (e.target === e.currentTarget) closeAccountsModal(); });
  el('account-form').addEventListener('submit', submitAccountForm);
  el('account-cancel-btn').addEventListener('click', resetAccountForm);

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
    if (el('nav-drawer').classList.contains('is-open')) closeNavDrawer();
    else if (!el('collections-modal').classList.contains('hidden')) closeCollectionsModal();
    else if (!el('accounts-modal').classList.contains('hidden')) closeAccountsModal();
    else if (!el('detail-modal').classList.contains('hidden')) closeDetail();
    else if (!el('add-modal').classList.contains('hidden')) closeAddModal();
    else if (vaultOpen) closeVault();
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
const KUDOS_LABELS = {
  GIVEN: '♥ Kudosed', NOT_DETECTED: 'Not given', UNKNOWN: '',
};

// ─── Helpers ──────────────────────────────────────────────────────────────────

function el(id) { return document.getElementById(id); }

function usernameFromToken() {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - b64.length % 4) % 4);
    return JSON.parse(atob(padded)).sub || '';
  } catch { return ''; }
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

// ─── Vault Privacy ────────────────────────────────────────────────────────────

function loadVaultState() {
  privacyMode = localStorage.getItem('sv_privacy_mode') === '1';
  updatePrivacyModeBtn();
  vaultOpen = !privacyMode && rememberVault && localStorage.getItem('sv_vault_open') === '1';
  applyVaultState(false);
}

function applyVaultState(animate) {
  const stage      = el('vault-stage');
  const closedScr  = el('vault-closed-screen');
  const toggleBtn  = el('vault-toggle-btn');

  if (vaultOpen) {
    closedScr.classList.add('hidden');
    stage.classList.remove('hidden');
    if (animate) {
      stage.classList.remove('vault-fade-in');
      void stage.offsetWidth; // force reflow
      stage.classList.add('vault-fade-in');
    }
    if (toggleBtn) toggleBtn.textContent = '▲ Close Vault';
  } else {
    stage.classList.add('hidden');
    closedScr.classList.remove('hidden');
    if (animate) {
      closedScr.classList.remove('vault-fade-in');
      void closedScr.offsetWidth;
      closedScr.classList.add('vault-fade-in');
    }
    if (toggleBtn) toggleBtn.textContent = '▼ Open Vault';
  }
}

function openVault() {
  vaultOpen = true;
  if (!privacyMode && rememberVault) localStorage.setItem('sv_vault_open', '1');
  applyVaultState(true);
  if (!dataLoaded) {
    dataLoaded = true;
    fetchStories();
    fetchCollections();
    fetchLabels();
  }
}

function closeVault() {
  vaultOpen = false;
  if (!privacyMode) localStorage.removeItem('sv_vault_open');
  applyVaultState(true);
}

function togglePrivacyMode() {
  privacyMode = !privacyMode;
  if (privacyMode) {
    localStorage.setItem('sv_privacy_mode', '1');
    localStorage.removeItem('sv_vault_open');
  } else {
    localStorage.removeItem('sv_privacy_mode');
  }
  updatePrivacyModeBtn();
}

function updatePrivacyModeBtn() {
  const badge = el('nav-privacy-status');
  if (badge) badge.textContent = privacyMode ? 'On' : 'Off';
  if (currentView === 'settings') updateSettingsPage();
}

// ─── Navigation ───────────────────────────────────────────────────────────────

function openNavDrawer() {
  el('nav-drawer').classList.add('is-open');
  el('nav-overlay').classList.add('is-open');
  el('menu-btn').setAttribute('aria-expanded', 'true');
  el('nav-drawer').setAttribute('aria-hidden', 'false');
}

function closeNavDrawer() {
  el('nav-drawer').classList.remove('is-open');
  el('nav-overlay').classList.remove('is-open');
  el('menu-btn').setAttribute('aria-expanded', 'false');
  el('nav-drawer').setAttribute('aria-hidden', 'true');
}

function navigateTo(view) {
  closeNavDrawer();
  currentView = view;
  document.querySelectorAll('.view').forEach(v => v.classList.add('hidden'));
  const target = el('view-' + view);
  if (target) target.classList.remove('hidden');
  document.querySelectorAll('.nav-item[data-view]').forEach(btn => {
    btn.classList.toggle('nav-active', btn.dataset.view === view);
  });
  if (view === 'collections') renderCollectionsPage();
  if (view === 'downloads')   renderDownloadsPage();
  if (view === 'statistics')  renderStatisticsPage();
  if (view === 'timeline')    renderTimelinePage();
  if (view === 'import')      renderImportPage();
  if (view === 'accounts')    renderAccountsPage();
  if (view === 'settings')    updateSettingsPage();
}

function renderCollectionsPage() {
  const grid = el('page-collections-grid');
  if (!grid) return;
  if (allCollections.length === 0) {
    grid.innerHTML = '<p class="page-empty-note">No collections yet. Use the button above to create one.</p>';
    return;
  }
  grid.innerHTML = allCollections.map(c => `
    <div class="page-collection-card">
      <p class="page-coll-name">${esc(c.name)}</p>
      <p class="page-coll-count">${c.storyCount} ${c.storyCount === 1 ? 'story' : 'stories'}</p>
      <div class="page-coll-actions">
        <button class="btn btn-ghost btn-sm page-coll-filter-btn" data-id="${c.id}" data-name="${escA(c.name)}"
          title="Show only stories in this collection">Browse</button>
      </div>
    </div>`).join('');
  grid.querySelectorAll('.page-coll-filter-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      navigateTo('library');
      const sel = el('filter-collection');
      if (sel) { sel.value = btn.dataset.id; applyFilters(); }
    });
  });
}

const IMPORT_TYPES = [
  { platform: 'AO3',      importType: 'HISTORY',       label: 'AO3 History',
    desc: 'Import your complete AO3 reading history.' },
  { platform: 'AO3',      importType: 'BOOKMARKS',     label: 'AO3 Bookmarks',
    desc: 'Import all bookmarked works from your AO3 account.' },
  { platform: 'AO3',      importType: 'SUBSCRIPTIONS', label: 'AO3 Subscriptions',
    desc: 'Import works and series you subscribe to on AO3.' },
  { platform: 'WATTPAD',  importType: 'LIBRARY',       label: 'Wattpad Library',
    desc: 'Import your Wattpad reading library and following list.' },
  { platform: 'FFN',      importType: 'FAVORITES',     label: 'FFN Favorites',
    desc: 'Import favorites and followed stories from FanFiction.net.' },
];

const JOB_STATUS_LABELS = {
  PENDING: 'Queued', RUNNING: 'Running', PAUSED: 'Paused',
  COMPLETED: 'Done', FAILED: 'Failed', CANCELLED: 'Cancelled',
};

const AO3_IMPORT_TYPES = ['HISTORY', 'BOOKMARKS', 'SUBSCRIPTIONS'];

async function renderImportPage() {
  const container = el('import-page-body');
  if (!container) return;

  let jobs = [];
  try {
    const res = await fetch(`${API}/imports`, { headers: { Authorization: `Bearer ${token}` } });
    if (res.status === 401) { handleUnauthorized(); return; }
    const body = await res.json();
    jobs = body.data || [];
  } catch { /* leave jobs empty */ }

  // Find most-recent job per (platform, importType)
  const latestJob = (platform, importType) =>
    jobs.find(j => j.platform === platform && j.importType === importType) || null;

  const renderCard = (importType) => {
    const live = importType.platform === 'AO3' && AO3_IMPORT_TYPES.includes(importType.importType);
    const job  = live ? latestJob(importType.platform, importType.importType) : null;
    const status = job ? job.status : null;

    const progressBar = job && job.totalPages
      ? `<div class="import-progress-bar"><div class="import-progress-fill" style="width:${Math.min(100, Math.round(job.currentPage / job.totalPages * 100))}%"></div></div>`
      : '';

    const meta = job ? `
      <div class="import-card-meta">
        <span class="import-job-status import-job-status-${job.status.toLowerCase()}">${JOB_STATUS_LABELS[job.status] || job.status}</span>
        ${job.itemsProcessed > 0 ? `<span class="import-card-count">${job.itemsProcessed} items</span>` : ''}
        ${job.currentPage > 0 ? `<span class="import-card-count">page ${job.currentPage}${job.totalPages ? '/' + job.totalPages : ''}</span>` : ''}
        ${job.createdAt ? `<span class="import-card-date">${job.createdAt.slice(0, 10)}</span>` : ''}
      </div>
      ${progressBar}` : '';

    if (!live) {
      return `
        <div class="import-card">
          <div class="import-card-body">
            <p class="import-card-title">${esc(importType.label)}</p>
            <p class="import-card-desc">${esc(importType.desc)}</p>
          </div>
          <div class="import-card-foot">
            <span class="import-coming-badge">Coming soon</span>
          </div>
        </div>`;
    }

    const canStart  = !job || status === 'COMPLETED' || status === 'FAILED' || status === 'CANCELLED';
    const canPause  = status === 'RUNNING';
    const canResume = status === 'PAUSED';
    const canCancel = status === 'PENDING' || status === 'RUNNING' || status === 'PAUSED';

    return `
      <div class="import-card" data-platform="${escA(importType.platform)}" data-type="${escA(importType.importType)}" data-job-id="${job ? job.id : ''}">
        <div class="import-card-body">
          <p class="import-card-title">${esc(importType.label)}</p>
          <p class="import-card-desc">${esc(importType.desc)}</p>
          ${meta}
        </div>
        <div class="import-card-foot">
          ${canStart  ? `<button class="btn btn-sm import-btn-start">Start Import</button>`  : ''}
          ${canPause  ? `<button class="btn btn-sm import-btn-pause">Pause</button>`          : ''}
          ${canResume ? `<button class="btn btn-sm import-btn-resume">Resume</button>`        : ''}
          ${canCancel ? `<button class="btn btn-ghost btn-sm import-btn-cancel">Cancel</button>` : ''}
        </div>
      </div>`;
  };

  const historyRows = jobs.length === 0
    ? '<p class="stat-empty">No imports have been run yet.</p>'
    : `<ul class="import-job-list">${jobs.map(j => `
        <li class="import-job-row">
          <span class="import-job-label">${esc(j.platform)} — ${esc(j.importType.replace(/_/g,' '))}</span>
          <span class="import-job-status import-job-status-${j.status.toLowerCase()}">${JOB_STATUS_LABELS[j.status] || j.status}</span>
          <span class="import-job-count">${j.itemsProcessed > 0 ? j.itemsProcessed + ' items' : ''}</span>
          <span class="import-job-date">${j.createdAt ? j.createdAt.slice(0,10) : ''}</span>
        </li>`).join('')}</ul>`;

  container.innerHTML = `
    <p class="import-intro">AO3 imports run through the StoryVault browser extension while you are logged into AO3. Wattpad and FFN support coming soon.</p>
    <div class="import-cards">
      ${IMPORT_TYPES.map(renderCard).join('')}
    </div>
    <div class="import-history-section">
      <h3 class="stat-panel-title">Import history</h3>
      ${historyRows}
    </div>`;

  // Wire card buttons
  container.querySelectorAll('.import-card[data-platform]').forEach(card => {
    const platform = card.dataset.platform;
    const importType = card.dataset.type;
    const jobId = card.dataset.jobId ? Number(card.dataset.jobId) : null;

    const doAction = async (action, bodyStr = null) => {
      try {
        const opts = { method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } };
        if (bodyStr) opts.body = bodyStr;
        const res = await fetch(`${API}/imports${action}`, opts);
        if (res.status === 401) { handleUnauthorized(); return; }
        await renderImportPage();
      } catch { /* silently retry on next render */ }
    };

    card.querySelector('.import-btn-start')?.addEventListener('click', async () => {
      // Create a new job then start it
      const createRes = await fetch(`${API}/imports`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ platform, importType }),
      });
      if (!createRes.ok) return;
      const newJob = (await createRes.json()).data;
      await doAction(`/${newJob.id}/start`);
    });

    card.querySelector('.import-btn-pause')?.addEventListener('click',  () => doAction(`/${jobId}/pause`));
    card.querySelector('.import-btn-resume')?.addEventListener('click', () => doAction(`/${jobId}/resume`));
    card.querySelector('.import-btn-cancel')?.addEventListener('click', () => doAction(`/${jobId}/cancel`));
  });
}

// ── Timeline ──────────────────────────────────────────────────────────────────

const TIMELINE_ICONS = {
  STORY_FIRST_SEEN: '✦', STORY_REVISITED: '↩', CHAPTER_PROGRESS_UPDATED: '📖',
  READING_STATUS_CHANGED: '⇄', KUDOS_GIVEN: '♥', COLLECTION_ADDED: '📂',
  COLLECTION_REMOVED: '✂', NOTE_ADDED: '✏', NOTE_EDITED: '✏',
  PERSONAL_LABEL_ADDED: '🏷', DOWNLOAD_RECORDED: '⬇', IMPORT_COMPLETED: '⬆',
};

const TIMELINE_LABELS = {
  STORY_FIRST_SEEN: 'Added to vault', STORY_REVISITED: 'Revisited',
  CHAPTER_PROGRESS_UPDATED: 'Progress updated', READING_STATUS_CHANGED: 'Status changed',
  KUDOS_GIVEN: 'Left kudos', COLLECTION_ADDED: 'Added to collection',
  COLLECTION_REMOVED: 'Removed from collection', NOTE_ADDED: 'Note added',
  NOTE_EDITED: 'Note edited', PERSONAL_LABEL_ADDED: 'Label added',
  DOWNLOAD_RECORDED: 'Downloaded', IMPORT_COMPLETED: 'Import completed',
};

let timelineState = { page: 0, size: 50, totalPages: 1, filter: {}, loading: false };

async function renderTimelinePage() {
  const container = el('timeline-page-body');
  if (!container) return;
  container.innerHTML = `<p class="loading-state" style="padding:12px 0;">${t('ui.loading')}</p>`;

  const now = new Date();
  const defaultFrom = new Date(now.getFullYear(), now.getMonth(), 1).toISOString().slice(0, 10);
  const defaultTo   = now.toISOString().slice(0, 10);

  timelineState = { page: 0, size: 50, totalPages: 1,
    filter: { fromDate: defaultFrom, toDate: defaultTo }, loading: false };

  container.innerHTML = `
    <div class="timeline-stats-row" id="tl-stats-row">
      <div class="timeline-stat-card"><div class="timeline-stat-label">Works opened</div><div class="timeline-stat-value" id="tl-stat-opened">—</div></div>
      <div class="timeline-stat-card"><div class="timeline-stat-label">Kudos given</div><div class="timeline-stat-value" id="tl-stat-kudos">—</div></div>
      <div class="timeline-stat-card"><div class="timeline-stat-label">Notes written</div><div class="timeline-stat-value" id="tl-stat-notes">—</div></div>
      <div class="timeline-stat-card"><div class="timeline-stat-label">Collections</div><div class="timeline-stat-value" id="tl-stat-colls">—</div></div>
      <div class="timeline-stat-card"><div class="timeline-stat-label">Words archived</div><div class="timeline-stat-value" id="tl-stat-words">—</div></div>
    </div>

    <div class="timeline-filters">
      <div class="timeline-filter-group">
        <span class="timeline-filter-label">From</span>
        <input type="date" id="tl-from" class="filter-select" value="${defaultFrom}">
      </div>
      <div class="timeline-filter-group">
        <span class="timeline-filter-label">To</span>
        <input type="date" id="tl-to" class="filter-select" value="${defaultTo}">
      </div>
      <div class="timeline-filter-group">
        <span class="timeline-filter-label">Event type</span>
        <select id="tl-type" class="filter-select">
          <option value="">All events</option>
          <option value="STORY_FIRST_SEEN">Added to vault</option>
          <option value="STORY_REVISITED">Revisited</option>
          <option value="CHAPTER_PROGRESS_UPDATED">Progress updated</option>
          <option value="READING_STATUS_CHANGED">Status changed</option>
          <option value="KUDOS_GIVEN">Kudos given</option>
          <option value="COLLECTION_ADDED">Collection added</option>
          <option value="COLLECTION_REMOVED">Collection removed</option>
          <option value="NOTE_ADDED">Note added</option>
          <option value="NOTE_EDITED">Note edited</option>
          <option value="PERSONAL_LABEL_ADDED">Label added</option>
          <option value="DOWNLOAD_RECORDED">Downloaded</option>
        </select>
      </div>
      <div class="timeline-filter-group">
        <span class="timeline-filter-label">Search</span>
        <input type="text" id="tl-search" class="filter-select" placeholder="Story title, note…" style="min-width:160px;">
      </div>
      <div class="timeline-filter-group" style="justify-content:flex-end;">
        <span class="timeline-filter-label">&nbsp;</span>
        <button class="btn btn-primary btn-sm" id="tl-apply-btn">Apply</button>
      </div>
    </div>

    <div class="timeline-jump-row">
      <span style="font-size:12px;color:var(--text-muted);">Jump to:</span>
      <select id="tl-jump-month" class="filter-select">
        ${['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
            .map((m, i) => `<option value="${i}"${i === now.getMonth() ? ' selected' : ''}>${m}</option>`).join('')}
      </select>
      <select id="tl-jump-year" class="filter-select">
        ${Array.from({length: 10}, (_, i) => now.getFullYear() - i)
            .map(y => `<option value="${y}"${y === now.getFullYear() ? ' selected' : ''}>${y}</option>`).join('')}
      </select>
      <button class="btn btn-ghost btn-sm" id="tl-jump-btn">Go</button>
    </div>

    <div id="tl-events-container"></div>
    <div class="timeline-load-more" id="tl-load-more" style="display:none;">
      <button class="btn btn-ghost" id="tl-more-btn">Load more</button>
    </div>`;

  el('tl-apply-btn').addEventListener('click', () => {
    timelineState.page = 0;
    timelineState.filter = buildTimelineFilter();
    loadTimelineEvents(true);
    loadTimelineStats();
  });

  el('tl-jump-btn').addEventListener('click', () => {
    const month = parseInt(el('tl-jump-month').value);
    const year  = parseInt(el('tl-jump-year').value);
    const from  = new Date(year, month, 1).toISOString().slice(0, 10);
    const to    = new Date(year, month + 1, 0).toISOString().slice(0, 10);
    el('tl-from').value = from;
    el('tl-to').value   = to;
    timelineState.page = 0;
    timelineState.filter = buildTimelineFilter();
    loadTimelineEvents(true);
    loadTimelineStats();
  });

  el('tl-more-btn')?.addEventListener('click', () => {
    timelineState.page++;
    loadTimelineEvents(false);
  });

  timelineState.filter = buildTimelineFilter();
  await Promise.all([loadTimelineEvents(true), loadTimelineStats()]);
}

function buildTimelineFilter() {
  const type = el('tl-type')?.value;
  return {
    fromDate: el('tl-from')?.value || null,
    toDate:   el('tl-to')?.value   || null,
    eventTypes: type ? [type] : [],
    search: el('tl-search')?.value?.trim() || null,
    page: 0,
    size: timelineState.size,
  };
}

async function loadTimelineEvents(replace) {
  if (timelineState.loading) return;
  timelineState.loading = true;
  const container = el('tl-events-container');
  if (!container) { timelineState.loading = false; return; }
  if (replace) container.innerHTML = `<p class="loading-state">${t('ui.loading')}</p>`;

  try {
    const body = { ...timelineState.filter, page: timelineState.page, size: timelineState.size };
    const res = await fetch(`${API}/timeline`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
    if (res.status === 401) { handleUnauthorized(); return; }
    const json = await res.json();
    const data = json.data;
    const events = data.content || [];
    timelineState.totalPages = data.totalPages || 1;

    if (replace) container.innerHTML = '';
    if (events.length === 0 && replace) {
      container.innerHTML = '<p class="page-empty-note">No events found.</p>';
      el('tl-load-more').style.display = 'none';
      return;
    }

    const grouped = groupByDay(events);
    let lastMonth = null;
    const frag = document.createDocumentFragment();
    for (const [day, dayEvents] of grouped) {
      const dayDate = new Date(day + 'T00:00:00');
      const monthKey = dayDate.getFullYear() + '-' + String(dayDate.getMonth() + 1).padStart(2, '0');
      if (monthKey !== lastMonth) {
        lastMonth = monthKey;
        const mh = document.createElement('div');
        mh.className = 'timeline-month-header';
        mh.textContent = dayDate.toLocaleString('default', { month: 'long', year: 'numeric' });
        frag.appendChild(mh);
      }
      const group = document.createElement('div');
      group.className = 'timeline-day-group';
      const dh = document.createElement('div');
      dh.className = 'timeline-day-header';
      dh.textContent = dayDate.toLocaleDateString('default', { weekday: 'long', month: 'long', day: 'numeric' });
      group.appendChild(dh);
      dayEvents.forEach(ev => group.appendChild(renderTimelineEvent(ev)));
      frag.appendChild(group);
    }
    container.appendChild(frag);

    const hasMore = timelineState.page < timelineState.totalPages - 1;
    el('tl-load-more').style.display = hasMore ? 'block' : 'none';
  } catch {
    if (replace) container.innerHTML = '<p class="page-empty-note">Could not load timeline.</p>';
  } finally {
    timelineState.loading = false;
  }
}

function groupByDay(events) {
  const map = new Map();
  events.forEach(ev => {
    const day = ev.eventTimestamp.slice(0, 10);
    if (!map.has(day)) map.set(day, []);
    map.get(day).push(ev);
  });
  return map;
}

function renderTimelineEvent(ev) {
  const div = document.createElement('div');
  div.className = 'timeline-event';
  const icon  = TIMELINE_ICONS[ev.eventType] || '·';
  const label = TIMELINE_LABELS[ev.eventType] || ev.eventType.replace(/_/g, ' ');
  const time  = ev.eventTimestamp ? ev.eventTimestamp.slice(11, 16) : '';
  const title = ev.storyTitle ? esc(ev.storyTitle) : '';
  const detail = buildEventDetail(ev);
  div.innerHTML = `
    <span class="timeline-event-icon">${icon}</span>
    <div class="timeline-event-body">
      <div class="timeline-event-title">${title ? title + ' — ' : ''}<span style="color:var(--text-muted);font-weight:400;">${esc(label)}</span></div>
      ${detail ? `<div class="timeline-event-detail">${detail}</div>` : ''}
    </div>
    <span class="timeline-event-time">${time}</span>`;
  return div;
}

function buildEventDetail(ev) {
  try {
    const m = ev.metadata ? JSON.parse(ev.metadata) : {};
    switch (ev.eventType) {
      case 'CHAPTER_PROGRESS_UPDATED':
        return m.from != null ? `Chapter ${m.from} → ${m.to}` : '';
      case 'READING_STATUS_CHANGED':
        return m.from && m.to ? `${m.from.replace(/_/g, ' ')} → ${m.to.replace(/_/g, ' ')}` : '';
      case 'COLLECTION_ADDED': case 'COLLECTION_REMOVED':
        return m.collectionName ? esc(m.collectionName) : '';
      case 'NOTE_ADDED': case 'NOTE_EDITED':
        return m.preview ? `"${esc(m.preview.slice(0, 80))}"` : '';
      case 'PERSONAL_LABEL_ADDED':
        return m.labelName ? esc(m.labelName) : '';
      case 'STORY_FIRST_SEEN': case 'STORY_REVISITED':
        return m.fandom ? esc(m.fandom) : '';
      default: return '';
    }
  } catch { return ''; }
}

async function loadTimelineStats() {
  const from = el('tl-from')?.value;
  const to   = el('tl-to')?.value;
  const params = new URLSearchParams();
  if (from) params.append('from', from);
  if (to)   params.append('to', to);
  try {
    const res = await fetch(`${API}/timeline/stats?${params}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) return;
    const json = await res.json();
    const d = json.data;
    const fmt = n => (n ?? 0).toLocaleString();
    const setVal = (id, val) => { const el2 = el(id); if (el2) el2.textContent = val; };
    setVal('tl-stat-opened', fmt(d.worksOpened));
    setVal('tl-stat-kudos',  fmt(d.kudosGiven));
    setVal('tl-stat-notes',  fmt(d.notesWritten));
    setVal('tl-stat-colls',  fmt(d.collectionsCreated));
    setVal('tl-stat-words',  fmt(d.totalWordsArchived));
  } catch {}
}

async function renderStatisticsPage() {
  const container = el('stats-page-body');
  if (!container) return;
  container.innerHTML = `<p class="loading-state" style="padding:12px 0;">${t('ui.loading')}</p>`;
  try {
    const res = await fetch(`${API}/stats`, { headers: { Authorization: `Bearer ${token}` } });
    if (res.status === 401) { handleUnauthorized(); return; }
    const body = await res.json();
    const d = body.data;

    const fmt = n => (n ?? 0).toLocaleString();
    const pct = (n, total) => total > 0 ? Math.round((n / total) * 100) : 0;

    const statusBar = (map, total) => {
      if (!map || Object.keys(map).length === 0) return '<p class="stat-empty">No data</p>';
      return Object.entries(map).sort((a, b) => b[1] - a[1]).map(([k, v]) =>
        `<div class="stat-bar-row">
           <span class="stat-bar-label">${esc(k.replace(/_/g, ' '))}</span>
           <div class="stat-bar-track"><div class="stat-bar-fill" style="width:${pct(v, total)}%"></div></div>
           <span class="stat-bar-count">${fmt(v)}</span>
         </div>`).join('');
    };

    const topList = (items, emptyMsg) => {
      if (!items || items.length === 0) return `<p class="stat-empty">${emptyMsg}</p>`;
      const max = items[0].count;
      return items.map(item =>
        `<div class="stat-bar-row">
           <span class="stat-bar-label" title="${escA(item.label)}">${esc(item.label)}</span>
           <div class="stat-bar-track"><div class="stat-bar-fill" style="width:${pct(item.count, max)}%"></div></div>
           <span class="stat-bar-count">${fmt(item.count)}</span>
         </div>`).join('');
    };

    const accessList = (items, showCount) => {
      if (!items || items.length === 0) return '<p class="stat-empty">No reading history</p>';
      return `<ol class="stat-story-list">${items.map(s =>
        `<li class="stat-story-item">
           <span class="stat-story-title">${esc(s.storyTitle || 'Untitled')}</span>
           <span class="stat-story-meta">${showCount
             ? fmt(s.accessCount) + ' access' + (s.accessCount === 1 ? '' : 'es')
             : (s.lastAccessedAt ? s.lastAccessedAt.slice(0, 10) : '')
           }</span>
         </li>`).join('')}</ol>`;
    };

    const totalStories = d.totalStories ?? 0;

    container.innerHTML = `
      <div class="stats-grid">

        <div class="stat-card stat-card-accent">
          <p class="stat-card-label">Stories saved</p>
          <p class="stat-card-value">${fmt(d.totalStories)}</p>
        </div>
        <div class="stat-card stat-card-accent">
          <p class="stat-card-label">Words archived</p>
          <p class="stat-card-value">${fmt(d.totalWordsArchived)}</p>
        </div>
        <div class="stat-card">
          <p class="stat-card-label">Kudosed</p>
          <p class="stat-card-value">${fmt(d.kudosedCount)}</p>
        </div>
        <div class="stat-card">
          <p class="stat-card-label">Collections</p>
          <p class="stat-card-value">${fmt(d.collectionsCount)}</p>
        </div>
        <div class="stat-card">
          <p class="stat-card-label">Connected accounts</p>
          <p class="stat-card-value">${fmt(d.connectedAccountsCount)}</p>
        </div>
        <div class="stat-card">
          <p class="stat-card-label">With my notes</p>
          <p class="stat-card-value">${fmt(d.storiesWithNotes)}</p>
        </div>
        <div class="stat-card">
          <p class="stat-card-label">With labels</p>
          <p class="stat-card-value">${fmt(d.labeledStoriesCount)}</p>
        </div>

        <div class="stat-panel stat-panel-half">
          <h3 class="stat-panel-title">Works by official status</h3>
          ${statusBar(d.byStoryStatus, totalStories)}
        </div>
        <div class="stat-panel stat-panel-half">
          <h3 class="stat-panel-title">Works by reading status</h3>
          ${statusBar(d.byReadingStatus, totalStories)}
        </div>

        <div class="stat-panel">
          <h3 class="stat-panel-title">Top fandoms</h3>
          ${topList(d.topFandoms, 'No stories saved yet')}
        </div>
        <div class="stat-panel">
          <h3 class="stat-panel-title">Top authors</h3>
          ${topList(d.topAuthors, 'No stories saved yet')}
        </div>
        <div class="stat-panel">
          <h3 class="stat-panel-title">Top ships &amp; relationships</h3>
          ${topList(d.topRelationships, 'No relationship tags found')}
        </div>
        <div class="stat-panel">
          <h3 class="stat-panel-title">Top freeform tags</h3>
          ${topList(d.topTags, 'No tags found')}
        </div>

        <div class="stat-panel stat-panel-half">
          <h3 class="stat-panel-title">Most accessed</h3>
          ${accessList(d.mostAccessedStories, true)}
        </div>
        <div class="stat-panel stat-panel-half">
          <h3 class="stat-panel-title">Recently accessed</h3>
          ${accessList(d.recentlyAccessedStories, false)}
        </div>

        ${d.topLabels && d.topLabels.length > 0 ? `
        <div class="stat-panel stat-panel-half">
          <h3 class="stat-panel-title">Top personal labels</h3>
          ${topList(d.topLabels, 'No labels yet')}
        </div>` : ''}

      </div>`;
  } catch {
    container.innerHTML = '<p class="page-empty-note">Could not load statistics.</p>';
  }
}

async function renderDownloadsPage() {
  const container = el('downloads-page-body');
  if (!container) return;
  container.innerHTML = `<p class="loading-state" style="padding:12px 0;">${t('ui.loading')}</p>`;

  let allRecords = [];
  try {
    const res = await fetch(`${API}/downloads`, { headers: { Authorization: `Bearer ${token}` } });
    if (res.status === 401) { handleUnauthorized(); return; }
    const body = await res.json();
    allRecords = body.data || [];
  } catch {
    container.innerHTML = '<p class="page-empty-note">Could not load downloads.</p>';
    return;
  }

  const FILE_FORMATS = ['PDF', 'EPUB', 'HTML', 'MOBI', 'AZW3', 'OTHER'];
  const PLATFORMS_DL = ['AO3', 'WATTPAD', 'FFN', 'CUSTOM'];

  const filterBar = `
    <div class="dl-filter-bar">
      <select class="filter-select" id="dl-filter-format">
        <option value="">All formats</option>
        ${FILE_FORMATS.map(f => `<option value="${f}">${f}</option>`).join('')}
      </select>
      <select class="filter-select" id="dl-filter-platform">
        <option value="">All platforms</option>
        ${PLATFORMS_DL.map(p => `<option value="${p}">${p}</option>`).join('')}
      </select>
      <input class="filter-input" type="date" id="dl-filter-from" title="Downloaded from" />
      <input class="filter-input" type="date" id="dl-filter-to"   title="Downloaded to" />
      <input class="filter-input" type="text" id="dl-filter-fandom" placeholder="Fandom…" style="flex:1;min-width:100px;" />
      <button class="btn btn-ghost btn-sm" id="dl-filter-clear">Clear</button>
    </div>`;

  container.innerHTML = filterBar + '<div id="dl-record-list"></div>';

  const renderList = () => {
    const fmt      = el('dl-filter-format').value;
    const platform = el('dl-filter-platform').value;
    const fromStr  = el('dl-filter-from').value;
    const toStr    = el('dl-filter-to').value;
    const fandom   = el('dl-filter-fandom').value.trim().toLowerCase();

    const filtered = allRecords.filter(r => {
      if (fmt && r.fileType !== fmt) return false;
      if (platform && r.platform !== platform) return false;
      if (fromStr && r.downloadedAt && r.downloadedAt.slice(0, 10) < fromStr) return false;
      if (toStr   && r.downloadedAt && r.downloadedAt.slice(0, 10) > toStr)   return false;
      if (fandom  && !(r.storyFandom || '').toLowerCase().includes(fandom))    return false;
      return true;
    });

    const listEl = el('dl-record-list');
    if (filtered.length === 0) {
      listEl.innerHTML = `
        <div class="placeholder-body">
          <div class="placeholder-gem" aria-hidden="true">◇</div>
          <p class="placeholder-note">${allRecords.length === 0 ? 'No downloads saved yet.' : 'No downloads match the filters.'}</p>
        </div>`;
      return;
    }

    listEl.innerHTML = filtered.map(r => {
      const dlUrl = r.sourceUrl || r.storyOriginalUrl;
      const dlLink = dlUrl
        ? `<a class="dl-again-link" href="${escA(dlUrl)}" target="_blank" rel="noopener noreferrer">↗ Download Again</a>`
        : '';
      return `
        <div class="download-record-row">
          <div class="dl-meta">
            <span class="dl-title">${esc(r.storyTitle || 'Untitled')}</span>
            ${r.storyFandom || r.storyAuthor
              ? `<span class="dl-sub">${[r.storyFandom, r.storyAuthor].filter(Boolean).map(esc).join(' · ')}</span>`
              : ''}
            <span class="dl-chips">
              ${r.fileType  ? `<span class="dl-chip">${esc(r.fileType)}</span>` : ''}
              ${r.platform  ? `<span class="dl-chip dl-chip-platform">${esc(r.platform)}</span>` : ''}
            </span>
          </div>
          <div class="dl-row-right">
            <span class="dl-date">${r.downloadedAt ? r.downloadedAt.slice(0, 10) : ''}</span>
            ${dlLink}
          </div>
        </div>`;
    }).join('');
  };

  renderList();

  ['dl-filter-format', 'dl-filter-platform', 'dl-filter-from', 'dl-filter-to', 'dl-filter-fandom']
    .forEach(id => el(id)?.addEventListener('change', renderList));
  el('dl-filter-fandom')?.addEventListener('input', renderList);
  el('dl-filter-clear')?.addEventListener('click', () => {
    el('dl-filter-format').value   = '';
    el('dl-filter-platform').value = '';
    el('dl-filter-from').value     = '';
    el('dl-filter-to').value       = '';
    el('dl-filter-fandom').value   = '';
    renderList();
  });
}

async function renderAccountsPage() {
  const container = el('page-accounts-list');
  if (!container) return;
  container.innerHTML = `<p class="loading-state" style="padding:12px 0;">${t('ui.loading')}</p>`;
  try {
    const res = await fetch(`${API}/accounts`, { headers: { Authorization: `Bearer ${token}` } });
    if (res.status === 401) { handleUnauthorized(); return; }
    const body = await res.json();
    const accounts = body.data || [];
    if (accounts.length === 0) {
      container.innerHTML = '<p class="page-empty-note">No connected accounts yet.</p>';
      return;
    }
    container.innerHTML = accounts.map(a => `
      <div class="account-row">
        <div class="account-row-info">
          <span class="platform-badge">${t('platform.' + a.platform) || a.platform}</span>
          <span class="account-display-name">${esc(a.displayName)}</span>
          ${a.accountLabel ? `<span class="account-label-chip">${esc(a.accountLabel)}</span>` : ''}
          ${!a.syncEnabled ? '<span class="account-sync-off">sync off</span>' : ''}
        </div>
        <div class="account-row-actions">
          <button class="btn btn-ghost btn-sm" onclick="openAccountsModal()">Edit</button>
        </div>
      </div>`).join('');
  } catch {
    container.innerHTML = '<p class="fetch-error" style="padding:8px 0;">Could not load accounts.</p>';
  }
}

function syncToggle(btnId, statusId, isOn, label) {
  const btn = el(btnId), sts = el(statusId);
  if (btn) btn.classList.toggle('is-on', isOn);
  if (sts) sts.textContent = label;
}

function updateSettingsPage() {
  const theme   = document.documentElement.dataset.theme   || 'light';
  const density = document.documentElement.dataset.density || 'minimal';
  syncToggle('settings-theme-btn',          'settings-theme-status',          theme === 'dark',      theme === 'dark' ? t('settings.theme.dark') : t('settings.theme.light'));
  syncToggle('settings-density-btn',        'settings-density-status',        density === 'maximal', density === 'maximal' ? t('settings.theme.compact') : t('settings.theme.comfortable'));
  syncToggle('settings-privacy-btn',        'settings-privacy-status',        privacyMode,   privacyMode   ? t('ui.on') : t('ui.off'));
  syncToggle('settings-remember-vault-btn', 'settings-remember-vault-status', rememberVault, rememberVault ? t('ui.on') : t('ui.off'));
  syncToggle('settings-auto-log-btn',       'settings-auto-log-status',       autoLog,       autoLog       ? t('ui.on') : t('ui.off'));
  syncToggle('settings-show-toast-btn',     'settings-show-toast-status',     showToast,     showToast     ? t('ui.on') : t('ui.off'));
  syncToggle('settings-show-kudos-btn',     'settings-show-kudos-status',     showKudos,     showKudos     ? t('ui.on') : t('ui.off'));
  syncToggle('settings-show-chips-btn',     'settings-show-chips-status',     showChips,     showChips     ? t('ui.on') : t('ui.off'));
  renderLangSelector();
}

function renderLangSelector() {
  const grid = el('lang-selector');
  if (!grid) return;
  const current = i18n.getLang();
  grid.innerHTML = Object.entries(i18n.SUPPORTED_LANGS).map(([code, info]) => `
    <button class="lang-btn${code === current ? ' lang-btn-active' : ''}${!info.available ? ' lang-btn-unavailable' : ''}"
      data-lang="${code}" ${!info.available ? 'disabled title="Coming soon"' : ''}>
      ${esc(info.label)}
    </button>`).join('');
  grid.querySelectorAll('.lang-btn:not([disabled])').forEach(btn => {
    btn.addEventListener('click', () => {
      i18n.setLang(btn.dataset.lang);
      renderLangSelector();
      updateSettingsPage();
    });
  });
}

// ─── Connected Accounts ───────────────────────────────────────────────────────

const ACCOUNT_PLATFORM_LABELS = { AO3: 'AO3', FFN: 'FFN', WATTPAD: 'Wattpad', OTHER: 'Other' };

async function openAccountsModal() {
  el('accounts-modal').classList.remove('hidden');
  document.body.style.overflow = 'hidden';
  resetAccountForm();
  await renderAccountsList();
}

function closeAccountsModal() {
  el('accounts-modal').classList.add('hidden');
  document.body.style.overflow = '';
}

async function renderAccountsList() {
  const container = el('accounts-list');
  container.innerHTML = `<p class="loading-state" style="padding:12px 0;">${t('ui.loading')}</p>`;
  try {
    const res = await fetch(`${API}/accounts`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.status === 401) { handleUnauthorized(); return; }
    const body = await res.json();
    const accounts = body.data || [];
    if (accounts.length === 0) {
      container.innerHTML = '<p class="history-empty" style="padding:8px 0 16px;">No connected accounts yet.</p>';
      return;
    }
    container.innerHTML = accounts.map(a => `
      <div class="account-row" data-id="${a.id}">
        <div class="account-row-info">
          <span class="platform-badge">${t('platform.' + a.platform) || a.platform}</span>
          <span class="account-display-name">${esc(a.displayName)}</span>
          ${a.accountLabel ? `<span class="account-label-chip">${esc(a.accountLabel)}</span>` : ''}
          ${!a.syncEnabled ? '<span class="account-sync-off">sync off</span>' : ''}
        </div>
        <div class="account-row-actions">
          <button class="btn btn-ghost btn-sm account-edit-btn" data-id="${a.id}">Edit</button>
          <button class="btn btn-danger btn-sm account-delete-btn" data-id="${a.id}">Delete</button>
        </div>
      </div>`).join('');

    container.querySelectorAll('.account-edit-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const acct = accounts.find(a => String(a.id) === btn.dataset.id);
        if (acct) startEditAccount(acct);
      });
    });
    container.querySelectorAll('.account-delete-btn').forEach(btn => {
      btn.addEventListener('click', () => confirmDeleteAccount(btn.dataset.id, btn));
    });
  } catch {
    container.innerHTML = '<p class="fetch-error" style="padding:8px 0;">Could not load accounts.</p>';
  }
}

function resetAccountForm() {
  el('account-form').reset();
  el('account-editing-id').value = '';
  el('account-form-title').textContent = 'Add Account';
  el('account-submit-btn').textContent = 'Add Account';
  el('account-submit-btn').disabled = false;
  el('account-form-error').classList.add('hidden');
}

function startEditAccount(acct) {
  el('account-editing-id').value = acct.id;
  el('acct-platform').value      = acct.platform || 'AO3';
  el('acct-display-name').value  = acct.displayName || '';
  el('acct-profile-url').value   = acct.profileUrl  || '';
  el('acct-label').value         = acct.accountLabel || '';
  el('acct-notes').value         = acct.notes        || '';
  el('account-form-title').textContent  = 'Edit Account';
  el('account-submit-btn').textContent  = 'Save Changes';
  el('account-submit-btn').disabled     = false;
  el('account-form-error').classList.add('hidden');
  el('account-form').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

async function submitAccountForm(e) {
  e.preventDefault();
  const errEl  = el('account-form-error');
  const btn    = el('account-submit-btn');
  const editId = el('account-editing-id').value;

  errEl.classList.add('hidden');

  const displayName = el('acct-display-name').value.trim();
  if (!displayName) {
    errEl.textContent = 'Display name is required.';
    errEl.classList.remove('hidden');
    return;
  }

  const data = {
    platform:     el('acct-platform').value,
    displayName,
    profileUrl:   el('acct-profile-url').value.trim() || null,
    accountLabel: el('acct-label').value.trim()       || null,
    syncEnabled:  true,
    notes:        el('acct-notes').value.trim()        || null,
  };

  btn.disabled    = true;
  btn.textContent = 'Saving…';

  try {
    if (editId) {
      const res = await fetch(`${API}/accounts/${editId}`, {
        method:  'PUT',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body:    JSON.stringify(data),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    } else {
      const res = await fetch(`${API}/accounts`, {
        method:  'POST',
        headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        body:    JSON.stringify(data),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
    }
    resetAccountForm();
    await renderAccountsList();
  } catch (err) {
    errEl.textContent = `Failed to save: ${err.message}`;
    errEl.classList.remove('hidden');
    btn.disabled    = false;
    btn.textContent = editId ? 'Save Changes' : 'Add Account';
  }
}

async function confirmDeleteAccount(id, btn) {
  if (!btn.dataset.confirming) {
    btn.dataset.confirming = '1';
    btn.textContent = 'Confirm?';
    return;
  }
  btn.disabled    = true;
  btn.textContent = 'Deleting…';
  try {
    const res = await fetch(`${API}/accounts/${id}`, {
      method:  'DELETE',
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    await renderAccountsList();
  } catch (err) {
    btn.disabled    = false;
    btn.textContent = 'Delete failed';
  }
}
