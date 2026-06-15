const fs = require('fs');
const cssPath = 'c:\\Users\\USER\\Downloads\\USTC_learnX\\src\\main\\resources\\static\\style.css';

const newCss = `
/* --- STUDENT DASHBOARD ADDITIONAL PREMIUM STYLES --- */

/* Timeline for Today's Classes */
.timeline-list {
    position: relative;
    padding-left: 2rem;
    margin-top: 1rem;
}
.timeline-list::before {
    content: '';
    position: absolute;
    left: 7px;
    top: 0;
    bottom: 0;
    width: 2px;
    background: linear-gradient(to bottom, var(--accent-blue) 0%, var(--glass-border) 100%);
    border-radius: 2px;
}
.timeline-item {
    position: relative;
    margin-bottom: 1.5rem;
}
.timeline-marker {
    position: absolute;
    left: -2rem;
    top: 0.5rem;
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: var(--accent-blue);
    border: 3px solid var(--bg-primary);
    box-shadow: 0 0 10px rgba(17, 33, 45, 0.4);
    z-index: 2;
}

/* Calendar Grids */
.schedule-weekly-grid::-webkit-scrollbar {
    height: 8px;
}
.schedule-weekly-grid::-webkit-scrollbar-thumb {
    background: rgba(255,255,255,0.3);
    border-radius: 4px;
}
.calendar-day-cell {
    transition: all 0.3s ease;
}
.calendar-day-cell:hover {
    transform: translateY(-2px);
    background: rgba(255,255,255,0.4) !important;
    box-shadow: 0 5px 15px rgba(0,0,0,0.05);
}

/* Reactions */
.rx-btn {
    background: rgba(255,255,255,0.2);
    border: 1px solid var(--glass-border);
    padding: 0.4rem 0.8rem;
    border-radius: 20px;
    font-size: 0.85rem;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    gap: 0.4rem;
    color: var(--text-secondary);
    transition: all 0.25s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}
.rx-btn:hover {
    background: rgba(255,255,255,0.4);
    transform: scale(1.05);
}
.rx-btn.like-btn.active {
    background: rgba(16, 185, 129, 0.15);
    border-color: #10b981;
    color: #059669;
}
.rx-btn.like-btn.active i { color: #10b981; }
.rx-btn.dislike-btn.active {
    background: rgba(239, 68, 68, 0.15);
    border-color: #ef4444;
    color: #b91c1c;
}
.rx-btn.dislike-btn.active i { color: #ef4444; }

/* Profile Premium Card */
.profile-showcase-container {
    display: grid;
    grid-template-columns: 1fr 2fr;
    gap: 2rem;
    align-items: flex-start;
}
@media (max-width: 900px) {
    .profile-showcase-container { grid-template-columns: 1fr; }
}
.premium-profile-card {
    padding: 2rem;
    display: flex;
    flex-direction: column;
    align-items: center;
    text-align: center;
    gap: 1.2rem;
}
.profile-avatar-wrapper {
    position: relative;
    width: 140px;
    height: 140px;
    border-radius: 50%;
    margin: 0 auto;
    cursor: pointer;
    box-shadow: 0 10px 25px rgba(0,0,0,0.1), 0 0 0 4px rgba(255,255,255,0.4);
    transition: all 0.3s ease;
}
.profile-avatar-wrapper:hover {
    transform: scale(1.02);
    box-shadow: 0 15px 35px rgba(0,0,0,0.15), 0 0 0 6px rgba(255,255,255,0.6);
}
.profile-avatar-image, .profile-avatar-placeholder {
    width: 100%;
    height: 100%;
    border-radius: 50%;
    object-fit: cover;
}
.profile-avatar-placeholder {
    background: var(--accent-blue);
    color: white;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 3rem;
}
.avatar-edit-overlay {
    position: absolute;
    bottom: 0;
    right: 0;
    background: var(--accent-blue);
    color: #fff;
    width: 36px;
    height: 36px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 10px rgba(0,0,0,0.2);
    border: 2px solid #fff;
    transition: all 0.2s ease;
}
.profile-avatar-wrapper:hover .avatar-edit-overlay {
    transform: scale(1.1);
    background: var(--accent-indigo);
}
#avatar-file-input { display: none; }
.profile-card-name { font-size: 1.5rem; font-weight: 800; font-family: 'Space Grotesk', sans-serif; }
.profile-card-role { font-size: 0.95rem; color: var(--text-secondary); margin-top: -0.5rem; }
.profile-pills-row { display: flex; flex-wrap: wrap; justify-content: center; gap: 0.5rem; }
.profile-kpi-stats-grid {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 1rem;
    width: 100%;
    margin-top: 1rem;
}
.profile-kpi-box {
    background: rgba(255,255,255,0.3);
    border: 1px solid rgba(255,255,255,0.5);
    padding: 1rem;
    border-radius: 14px;
    display: flex;
    flex-direction: column;
    align-items: center;
}
.profile-kpi-box .lbl { font-size: 0.75rem; color: var(--text-muted); text-transform: uppercase; font-weight: 600; letter-spacing: 0.5px; margin-bottom: 0.2rem; }
.profile-kpi-box .val { font-size: 1rem; font-weight: 700; color: var(--accent-blue); font-family: 'Space Grotesk', sans-serif; }
.sensitive-field-indicator {
    font-size: 0.7rem;
    color: var(--danger);
    background: rgba(255, 69, 58, 0.1);
    padding: 2px 6px;
    border-radius: 10px;
    margin-left: 0.5rem;
    vertical-align: middle;
}

/* MCQ Quiz Styles */
.mcq-options-container {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    margin-top: 1rem;
}
.mcq-option-label {
    display: flex;
    align-items: center;
    gap: 0.75rem;
    padding: 1rem 1.25rem;
    background: rgba(255,255,255,0.4);
    border: 2px solid transparent;
    border-radius: 12px;
    cursor: pointer;
    transition: all 0.25s ease;
}
.mcq-option-label:hover {
    background: rgba(255,255,255,0.6);
}
.mcq-option-label input[type="radio"] {
    accent-color: var(--accent-blue);
    transform: scale(1.2);
}
.mcq-option-label.selected {
    background: rgba(17, 33, 45, 0.05);
    border-color: var(--accent-blue);
    box-shadow: 0 4px 15px rgba(17, 33, 45, 0.05);
}
.exam-taking-header {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    padding: 1.5rem;
    background: rgba(255,255,255,0.6);
    border: 1px solid var(--glass-border);
    border-radius: 16px;
    margin-bottom: 2rem;
    backdrop-filter: blur(10px);
}
.exam-timer-fixed {
    position: sticky;
    top: 100px;
    background: var(--accent-blue);
    color: white;
    padding: 0.75rem 1.5rem;
    border-radius: 30px;
    font-family: 'Space Grotesk', sans-serif;
    font-size: 1.25rem;
    font-weight: 700;
    box-shadow: 0 10px 30px rgba(17, 33, 45, 0.3);
    z-index: 10;
    animation: pulseGlow 2s infinite alternate;
}
.exam-question-item {
    background: rgba(255,255,255,0.25);
    border: 1px solid rgba(255,255,255,0.4);
    border-radius: 16px;
    padding: 2rem;
    margin-bottom: 1.5rem;
}
`;

fs.appendFileSync(cssPath, newCss, 'utf8');
console.log('Appended premium styles to style.css');
