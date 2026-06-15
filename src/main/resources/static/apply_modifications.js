const fs = require('fs');
const path = require('path');

const appJsPath = path.join(__dirname, 'app.js');
let appJs = fs.readFileSync(appJsPath, 'utf8');

// Normalize line endings to \n
appJs = appJs.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

// Clean up any previously appended student schedule code to start fresh
const sep = '// --- VIEW: TRIPLE-MODE SCHEDULE CALENDAR (STUDENT) ---';
if (appJs.includes(sep)) {
    console.log('Removing previously appended student schedule code...');
    appJs = appJs.split(sep)[0];
}

// Helper to replace and verify
function replaceAndVerify(name, search, replacement) {
    // Normalize line endings in search string too
    const normSearch = search.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    const normReplacement = replacement.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
    
    if (!appJs.includes(normSearch)) {
        console.error(`ERROR: Search string for "${name}" not found!`);
        return false;
    }
    
    // Check if it appears multiple times
    const parts = appJs.split(normSearch);
    if (parts.length > 2) {
        console.error(`ERROR: Search string for "${name}" found multiple times!`);
        return false;
    }
    
    appJs = parts.join(normReplacement);
    console.log(`SUCCESS: Replaced "${name}"`);
    return true;
}

// 1. Modify loadNotesLibrary
const oldNotesLib = `async function loadNotesLibrary() {
    const res = await apiFetch('/api/resources/approved');
    if (!res || !res.ok) return;

    activeResources = await res.json();
    renderFilteredNotes(activeResources);
}`;
const newNotesLib = `async function loadNotesLibrary() {
    const res = await apiFetch('/api/resources/approved');
    if (!res || !res.ok) return;

    activeResources = await res.json();
    activeResources.sort((a, b) => {
        const netA = (a.likesCount || 0) - (a.dislikesCount || 0);
        const netB = (b.likesCount || 0) - (b.dislikesCount || 0);
        return netB - netA;
    });
    renderFilteredNotes(activeResources);
}`;

// 2. Modify renderRoutineManager
const oldRoutineMgrStart = `// --- VIEW: ROUTINE / SCHEDULER MANAGER (CR) ---
function renderRoutineManager(container) {
    container.innerHTML = \``;
const newRoutineMgrCode = `// --- VIEW: ROUTINE / SCHEDULER MANAGER ---
function renderRoutineManager(container) {
    if (state.user.role === 'STUDENT') {
        renderStudentSchedule(container);
        return;
    }

    container.innerHTML = \``;

// 3. Modify Exams to add tabs
const oldExamsHtml = `        <div class="glass-panel section-card">
            <h3>Active Assessment Exams</h3>
            <div class="routine-table-container">`;
const newExamsHtml = `        <div class="glass-panel section-card">
            <div style="display: flex; gap: 1rem; margin-bottom: 1rem; border-bottom: 1px solid var(--glass-border); padding-bottom: 1rem;">
                <button class="btn exam-tab" id="tab-active-exams" style="width: auto; background: linear-gradient(135deg, var(--accent-blue) 0%, var(--accent-indigo) 100%); color: #fff;">Active & Upcoming</button>
                <button class="btn btn-secondary exam-tab" id="tab-past-exams" style="width: auto;">Submitted & Past</button>
            </div>
            <div class="routine-table-container">`;

// 4. Modify loadExamsList call in renderExams
const oldLoadExamsListCall = `    loadExamsList();\n}`;
const newLoadExamsListCall = `    document.getElementById('tab-active-exams').addEventListener('click', (e) => {
        document.querySelectorAll('.exam-tab').forEach(t => {
            t.classList.add('btn-secondary');
            t.style.background = 'transparent';
            t.style.color = 'var(--text-primary)';
        });
        e.target.classList.remove('btn-secondary');
        e.target.style.background = 'linear-gradient(135deg, var(--accent-blue) 0%, var(--accent-indigo) 100%)';
        e.target.style.color = '#fff';
        loadExamsList('ACTIVE');
    });

    document.getElementById('tab-past-exams').addEventListener('click', (e) => {
        document.querySelectorAll('.exam-tab').forEach(t => {
            t.classList.add('btn-secondary');
            t.style.background = 'transparent';
            t.style.color = 'var(--text-primary)';
        });
        e.target.classList.remove('btn-secondary');
        e.target.style.background = 'linear-gradient(135deg, var(--accent-blue) 0%, var(--accent-indigo) 100%)';
        e.target.style.color = '#fff';
        loadExamsList('PAST');
    });

    loadExamsList('ACTIVE');\n}`;

// 5. Modify loadExamsList definition
const oldLoadExamsFuncStart = `async function loadExamsList() {
    const res = await apiFetch('/api/exams');
    if (!res || !res.ok) return;

    const list = await res.json();
    const tbody = document.getElementById('exams-list-tbody');
    tbody.innerHTML = '';

    if (list.length === 0) {`;
const newLoadExamsFuncStart = `async function loadExamsList(tab = 'ACTIVE') {
    const res = await apiFetch('/api/exams');
    if (!res || !res.ok) return;

    let list = await res.json();
    const tbody = document.getElementById('exams-list-tbody');
    tbody.innerHTML = '';

    const now = new Date();
    list = list.filter(exam => {
        const end = new Date(exam.endTime);
        const isPast = now > end || exam.alreadySubmitted;
        return tab === 'ACTIVE' ? !isPast : isPast;
    });

    if (list.length === 0) {`;

let allSuccess = true;
// Since we are running on already modified file, we need to restore it first or let replaceAndVerify run on it.
// Wait, since we are doing replaceAndVerify, but app.js has already been modified, the original strings (like oldRoutineMgrStart) won't be found anymore!
// To fix this, let's git checkout app.js or restore app.js from the unmodified repository.
// Wait! Is there an unmodified app.js we can restore?
// Oh! We don't have git checkout because it's not a git repository.
// But wait! Can we reconstruct the unmodified app.js by reversing the changes, or did we keep a backup?
// No backup. But wait, since we know exactly what we replaced, we can just replace the new versions with the old versions first to restore it, OR we can write a script that restores app.js from target/classes or by just replacing!
// Wait! The project is compiled, so the compiled target folder target/classes/static/app.js might contain the ORIGINAL, unmodified app.js!
// Oh! That is an incredibly brilliant insight!
// Let's check if target/classes/static/app.js exists!
// Let's run a node check or list_dir to see if target/classes/static/app.js is there.
// If it is, that is the original unmodified app.js!
// Let's list target/classes/static to verify.
