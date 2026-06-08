'use strict';

const API = 'http://localhost:8080/api/v1';

let allStories = [];
let allCollections = [];
let token = localStorage.getItem('sv_token');
let editingStoryId = null;
let advancedMode = false;      // true when showing POST /search results
let advPanelOpen = false;
let quickFilters = {};         // active field-specific filters: {author, fandom, tag, relationship}
let vaultOpen = false;
let privacyMode = false;
let dataLoaded = false;
let currentView = 'library';

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
  navigateTo('library');
  loadVaultState();
  if (vaultOpen) {
    dataLoaded = true;
    fetchStories();
    fetchCollections();
  }
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
  container.innerHTML = '<p class="loading-state" style="padding:12px 0;">Loading…</p>';
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

  const search     = el('search-input').value.toLowerCase().trim();
  const platform   = el('filter-platform').value;
  const status     = el('filter-status').value;
  const kudos      = el('filter-kudos').value;
  const collFilter = el('filter-collection').value;

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
  if (kudos === 'GIVEN')     list = list.filter(s => s.kudosStatus === 'GIVEN');
  if (kudos === 'NOT_GIVEN') list = list.filter(s => s.kudosStatus !== 'GIVEN');
  if (collFilter) list = list.filter(s =>
    (s.collections || []).some(c => String(c.id) === collFilter));

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
  const fileIcon  = s.hasFile ? `<span class="card-file-icon" title="File attached">◎</span>` : '';
  const kudosIcon = s.kudosStatus === 'GIVEN' ? `<span class="card-kudos-icon" title="Kudosed">♥</span>` : '';
  const collChips = (s.collections || []).slice(0, 3).map(c =>
    `<span class="collection-chip collection-chip-card">${esc(c.name)}</span>`).join('');
  const collRow = collChips ? `<div class="card-collections">${collChips}</div>` : '';

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
      ${collRow}
      <div class="card-meta">
        <span class="platform-badge">${PLATFORM_LABELS[s.platform] || s.platform}</span>
        <div class="card-tags">${tags}</div>
        <div class="card-icons">${fileIcon}${kudosIcon}${urlIcon}</div>
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
    ...(s.kudosStatus && s.kudosStatus !== 'UNKNOWN'
        ? [['Kudos', KUDOS_LABELS[s.kudosStatus] || s.kudosStatus]] : []),
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
    <div id="detail-collections"></div>
    <div id="detail-notes"></div>
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

  loadDetailCollections(s.id, s.collections || []);
  loadReadingHistory(s.id);
  loadNotes(s.id);
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

// ─── Vault Privacy ────────────────────────────────────────────────────────────

function loadVaultState() {
  privacyMode = localStorage.getItem('sv_privacy_mode') === '1';
  updatePrivacyModeBtn();
  vaultOpen = !privacyMode && localStorage.getItem('sv_vault_open') === '1';
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
  if (!privacyMode) localStorage.setItem('sv_vault_open', '1');
  applyVaultState(true);
  if (!dataLoaded) {
    dataLoaded = true;
    fetchStories();
    fetchCollections();
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

async function renderImportPage() {
  const container = el('import-page-body');
  if (!container) return;

  const statusMap = { PENDING: 'Queued', RUNNING: 'Running', COMPLETED: 'Done',
                      FAILED: 'Failed', CANCELLED: 'Cancelled' };

  let jobsHtml = '';
  try {
    const res = await fetch(`${API}/imports`, { headers: { Authorization: `Bearer ${token}` } });
    if (res.status === 401) { handleUnauthorized(); return; }
    const body = await res.json();
    const jobs = body.data || [];
    if (jobs.length === 0) {
      jobsHtml = '<p class="stat-empty">No imports have been run yet.</p>';
    } else {
      jobsHtml = `<ul class="import-job-list">${jobs.map(j => `
        <li class="import-job-row">
          <span class="import-job-label">${esc(j.platform)} — ${esc(j.importType.replace(/_/g,' '))}</span>
          <span class="import-job-status import-job-status-${j.status.toLowerCase()}">${statusMap[j.status] || j.status}</span>
          <span class="import-job-count">${j.itemsProcessed > 0 ? j.itemsProcessed + ' items' : ''}</span>
          <span class="import-job-date">${j.createdAt ? j.createdAt.slice(0,10) : ''}</span>
        </li>`).join('')}</ul>`;
    }
  } catch {
    jobsHtml = '<p class="stat-empty">Could not load import history.</p>';
  }

  container.innerHTML = `
    <p class="import-intro">Imports use connected accounts or extension-assisted syncing when available. Select an import type below to queue a job when support is ready.</p>

    <div class="import-cards">
      ${IMPORT_TYPES.map(t => `
        <div class="import-card">
          <div class="import-card-body">
            <p class="import-card-title">${esc(t.label)}</p>
            <p class="import-card-desc">${esc(t.desc)}</p>
          </div>
          <div class="import-card-foot">
            <span class="import-coming-badge">Coming soon</span>
            <button class="btn btn-sm import-trigger-btn" disabled
              data-platform="${escA(t.platform)}" data-type="${escA(t.importType)}">
              Import ${esc(t.label)}
            </button>
          </div>
        </div>`).join('')}
    </div>

    <div class="import-history-section">
      <h3 class="stat-panel-title">Import history</h3>
      ${jobsHtml}
    </div>`;
}

async function renderStatisticsPage() {
  const container = el('stats-page-body');
  if (!container) return;
  container.innerHTML = '<p class="loading-state" style="padding:12px 0;">Loading…</p>';
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

      </div>`;
  } catch {
    container.innerHTML = '<p class="page-empty-note">Could not load statistics.</p>';
  }
}

async function renderDownloadsPage() {
  const container = el('downloads-page-body');
  if (!container) return;
  container.innerHTML = '<p class="loading-state" style="padding:12px 0;">Loading…</p>';
  try {
    const res = await fetch(`${API}/downloads`, { headers: { Authorization: `Bearer ${token}` } });
    if (res.status === 401) { handleUnauthorized(); return; }
    const body = await res.json();
    const records = body.data || [];
    if (records.length === 0) {
      container.innerHTML = `
        <div class="placeholder-body">
          <div class="placeholder-gem" aria-hidden="true">◇</div>
          <p class="placeholder-note">No downloads saved yet.</p>
          <p class="placeholder-note" style="margin-top:4px;opacity:.6;">Future downloads will appear here.</p>
        </div>`;
      return;
    }
    container.innerHTML = records.map(r => `
      <div class="download-record-row">
        <div class="dl-meta">
          <span class="dl-title">${esc(r.storyTitle || 'Untitled')}</span>
          <span class="dl-chips">
            <span class="dl-chip">${esc(r.fileType || '')}</span>
            <span class="dl-chip dl-chip-platform">${esc(r.platform || '')}</span>
          </span>
        </div>
        <span class="dl-date">${r.downloadedAt ? r.downloadedAt.slice(0, 10) : ''}</span>
      </div>`).join('');
  } catch {
    container.innerHTML = '<p class="page-empty-note">Could not load downloads.</p>';
  }
}

async function renderAccountsPage() {
  const container = el('page-accounts-list');
  if (!container) return;
  container.innerHTML = '<p class="loading-state" style="padding:12px 0;">Loading…</p>';
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
          <span class="platform-badge">${ACCOUNT_PLATFORM_LABELS[a.platform] || a.platform}</span>
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

function updateSettingsPage() {
  const pBtn = el('settings-privacy-btn');
  const pSts = el('settings-privacy-status');
  if (pBtn && pSts) {
    pBtn.classList.toggle('is-on', privacyMode);
    pSts.textContent = privacyMode ? 'On' : 'Off';
  }
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
  container.innerHTML = '<p class="loading-state" style="padding:12px 0;">Loading…</p>';
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
          <span class="platform-badge">${ACCOUNT_PLATFORM_LABELS[a.platform] || a.platform}</span>
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
