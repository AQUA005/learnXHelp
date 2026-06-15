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

// Add the correctly escaped student schedule code
const studentScheduleCode = `
// --- VIEW: TRIPLE-MODE SCHEDULE CALENDAR (STUDENT) ---
async function renderStudentSchedule(container) {
    container.innerHTML = \`
        <div class="view-header">
            <div class="view-title">
                <h2>Class Schedule & Calendar</h2>
                <p>View your daily timeline, weekly routine grid, and monthly calendar.</p>
            </div>
        </div>
        <div class="glass-panel section-card">
            <div style="display: flex; gap: 1rem; margin-bottom: 1.5rem; border-bottom: 1px solid var(--glass-border); padding-bottom: 1rem;">
                <button class="btn sched-tab active" data-tab="today" style="width: auto;">Today's Timeline</button>
                <button class="btn btn-secondary sched-tab" data-tab="weekly" style="width: auto;">Weekly Grid</button>
                <button class="btn btn-secondary sched-tab" data-tab="monthly" style="width: auto;">Monthly View</button>
            </div>
            <div id="schedule-content-host" style="min-height: 400px; position: relative;">
                <div style="text-align: center; color: var(--text-secondary); padding: 2rem;"><i class="fa-solid fa-spinner fa-spin"></i> Loading schedule data...</div>
            </div>
        </div>
    \`;

    const tabs = container.querySelectorAll('.sched-tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', (e) => {
            tabs.forEach(t => {
                t.classList.remove('active');
                t.classList.add('btn-secondary');
                t.style.background = 'transparent';
                t.style.color = 'var(--text-primary)';
            });
            e.target.classList.add('active');
            e.target.classList.remove('btn-secondary');
            e.target.style.background = 'linear-gradient(135deg, var(--accent-blue) 0%, var(--accent-indigo) 100%)';
            e.target.style.color = '#fff';
            
            const tabName = e.target.getAttribute('data-tab');
            renderScheduleTab(tabName);
        });
    });

    // Initialize styling for first active tab
    const firstTab = container.querySelector('.sched-tab.active');
    if (firstTab) {
        firstTab.classList.remove('btn-secondary');
        firstTab.style.background = 'linear-gradient(135deg, var(--accent-blue) 0%, var(--accent-indigo) 100%)';
        firstTab.style.color = '#fff';
    }

    // Load data
    const [routineRes, ctRes] = await Promise.all([
        apiFetch('/api/schedule/routine'),
        apiFetch('/api/schedule/ct')
    ]);

    if (routineRes && routineRes.ok && ctRes && ctRes.ok) {
        state.routineData = await routineRes.json();
        state.ctData = await ctRes.json();
        renderScheduleTab('today');
    }
}

function renderScheduleTab(tabName) {
    const host = document.getElementById('schedule-content-host');
    host.innerHTML = '';
    
    if (tabName === 'today') {
        const days = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
        const today = days[new Date().getDay()];
        const todayClasses = state.routineData.filter(item => item.dayOfWeek === today);
        todayClasses.sort((a, b) => a.startTime.localeCompare(b.startTime));

        if (todayClasses.length === 0) {
            host.innerHTML = \`<div style="text-align: center; color: var(--text-secondary); padding: 3rem;">No classes today.</div>\`;
            return;
        }

        let html = '<div class="timeline-list">';
        todayClasses.forEach(item => {
            html += \`
                <div class="timeline-item">
                    <div class="timeline-marker"></div>
                    <div class="timeline-card glass-panel" style="padding: 1rem; border-radius: 12px; margin-bottom: 1rem;">
                        <div class="timeline-time" style="font-weight:700; color:var(--accent-blue); margin-bottom:0.25rem;">\${item.startTime.substring(0,5)} - \${item.endTime.substring(0,5)}</div>
                        <div class="timeline-subject" style="font-size:1.1rem; margin-bottom:0.5rem;">\${item.courseName}</div>
                        <div class="timeline-details" style="font-size:0.85rem; color:var(--text-secondary); display:flex; gap:1rem;">
                            <span><i class="fa-solid fa-chalkboard-user"></i> \${item.teacherName || 'TBA'}</span>
                            <span><i class="fa-solid fa-location-dot"></i> Room \${item.roomNo || 'TBA'}</span>
                        </div>
                    </div>
                </div>
            \`;
        });
        html += '</div>';
        host.innerHTML = html;
    } else if (tabName === 'weekly') {
        // Build weekly grid
        const days = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
        
        let html = \`<div class="schedule-weekly-grid" style="display:grid; grid-template-columns: 100px repeat(6, 1fr); gap: 0.5rem; overflow-x: auto; padding-bottom: 1rem;">\`;
        
        // Header
        html += \`<div style="font-weight:700; color:var(--text-secondary); padding:0.5rem;">Time / Day</div>\`;
        for (let i=0; i<6; i++) {
            html += \`<div style="font-weight:700; color:var(--accent-blue); text-align:center; padding:0.5rem;">\${days[i].substring(0,3)}</div>\`;
        }

        // We assume timeslots are 08:30, 10:00, 11:30, 01:00, 02:30, 04:00
        const timeSlots = ["08:30", "10:00", "11:30", "13:00", "14:30", "16:00"];
        
        timeSlots.forEach(time => {
            html += \`<div style="font-family:'Space Grotesk',sans-serif; font-size:0.85rem; color:var(--text-secondary); display:flex; align-items:center; justify-content:flex-end; padding-right:1rem;">\${time}</div>\`;
            
            for (let i=0; i<6; i++) {
                const day = days[i];
                const matchingClass = state.routineData.find(c => c.dayOfWeek === day && c.startTime.startsWith(time));
                
                if (matchingClass) {
                    html += \`
                        <div class="glass-panel" style="padding:0.75rem; border-radius:12px; background:rgba(17,33,45,0.05); border-color:rgba(17,33,45,0.15); font-size:0.8rem; display:flex; flex-direction:column; gap:0.25rem;">
                            <strong style="color:var(--accent-blue);">\${matchingClass.courseName.split(' - ')[0]}</strong>
                            <span style="color:var(--text-secondary); font-size:0.75rem;">\${matchingClass.teacherName}</span>
                            <span style="color:var(--text-secondary); font-size:0.75rem;"><i class="fa-solid fa-location-dot"></i> \${matchingClass.roomNo}</span>
                        </div>
                    \`;
                } else {
                    html += \`<div class="glass-panel" style="background:transparent; border-style:dashed; border-color:rgba(0,0,0,0.05); border-radius:12px;"></div>\`;
                }
            }
        });
        
        html += \`</div>\`;
        host.innerHTML = html;
    } else if (tabName === 'monthly') {
        host.innerHTML = \`
            <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:1rem;">
                <h3 style="font-family:'Space Grotesk',sans-serif;">\${new Date().toLocaleString('default', { month: 'long', year: 'numeric' })}</h3>
                <div style="display:flex; gap:1rem;">
                    <span style="font-size:0.8rem;"><span style="display:inline-block; width:10px; height:10px; border-radius:50%; background:var(--danger); margin-right:4px;"></span>Exam/CT</span>
                    <span style="font-size:0.8rem;"><span style="display:inline-block; width:10px; height:10px; border-radius:50%; background:var(--accent-blue); margin-right:4px;"></span>Class</span>
                </div>
            </div>
            <div class="monthly-calendar-grid" style="display:grid; grid-template-columns: repeat(7, 1fr); gap:0.5rem; text-align:center;">
                <div style="font-weight:600; padding:0.5rem; color:var(--text-secondary);">Sun</div>
                <div style="font-weight:600; padding:0.5rem; color:var(--text-secondary);">Mon</div>
                <div style="font-weight:600; padding:0.5rem; color:var(--text-secondary);">Tue</div>
                <div style="font-weight:600; padding:0.5rem; color:var(--text-secondary);">Wed</div>
                <div style="font-weight:600; padding:0.5rem; color:var(--text-secondary);">Thu</div>
                <div style="font-weight:600; padding:0.5rem; color:var(--text-secondary);">Fri</div>
                <div style="font-weight:600; padding:0.5rem; color:var(--text-secondary);">Sat</div>
            </div>
            <div id="monthly-days-host" style="display:grid; grid-template-columns: repeat(7, 1fr); gap:0.5rem;"></div>
            
            <div id="monthly-selected-details" class="glass-panel" style="margin-top: 1.5rem; padding: 1rem; border-radius: 12px; display: none;"></div>
        \`;
        
        const daysHost = document.getElementById('monthly-days-host');
        const now = new Date();
        const year = now.getFullYear();
        const month = now.getMonth();
        const firstDay = new Date(year, month, 1).getDay();
        const daysInMonth = new Date(year, month + 1, 0).getDate();
        
        for (let i=0; i<firstDay; i++) {
            daysHost.innerHTML += \`<div></div>\`;
        }
        
        for (let i=1; i<=daysInMonth; i++) {
            const dateObj = new Date(year, month, i);
            const dayOfWeek = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][dateObj.getDay()];
            
            const hasClass = state.routineData.some(r => r.dayOfWeek === dayOfWeek);
            const ctsOnDay = state.ctData.filter(ct => new Date(ct.dateTime).toDateString() === dateObj.toDateString());
            
            let dots = '';
            if (hasClass) dots += \`<div style="width:6px; height:6px; border-radius:50%; background:var(--accent-blue);"></div>\`;
            if (ctsOnDay.length > 0) dots += \`<div style="width:6px; height:6px; border-radius:50%; background:var(--danger);"></div>\`;
            
            const isToday = i === now.getDate() ? 'border: 2px solid var(--accent-blue);' : 'border: 1px solid var(--glass-border);';
            
            daysHost.innerHTML += \`
                <div class="calendar-day-cell glass-panel interactive" data-date="\${dateObj.toISOString()}" style="padding:0.75rem 0.5rem; border-radius:12px; cursor:pointer; display:flex; flex-direction:column; align-items:center; gap:0.25rem; \${isToday}">
                    <span style="font-weight:600; font-family:'Space Grotesk',sans-serif;">\${i}</span>
                    <div style="display:flex; gap:4px; margin-top:2px; height:6px;">\${dots}</div>
                </div>
            \`;
        }
        
        document.querySelectorAll('.calendar-day-cell').forEach(cell => {
            cell.addEventListener('click', (e) => {
                const dateStr = e.currentTarget.getAttribute('data-date');
                const dateObj = new Date(dateStr);
                const dayOfWeek = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'][dateObj.getDay()];
                
                const classes = state.routineData.filter(r => r.dayOfWeek === dayOfWeek);
                const cts = state.ctData.filter(ct => new Date(ct.dateTime).toDateString() === dateObj.toDateString());
                
                const detailsHost = document.getElementById('monthly-selected-details');
                detailsHost.style.display = 'block';
                
                let detailsHtml = \`<h4 style="margin-bottom:0.75rem; border-bottom:1px solid rgba(0,0,0,0.1); padding-bottom:0.5rem;">Events for \${dateObj.toLocaleDateString()}</h4>\`;
                
                if (classes.length === 0 && cts.length === 0) {
                    detailsHtml += \`<p style="color:var(--text-secondary); font-size:0.9rem;">No classes or exams scheduled.</p>\`;
                }
                
                if (cts.length > 0) {
                    detailsHtml += \`<div style="margin-bottom:1rem;">\`;
                    cts.forEach(ct => {
                        detailsHtml += \`<div style="display:flex; gap:0.5rem; align-items:center; margin-bottom:0.5rem; color:var(--danger);">
                            <i class="fa-solid fa-fire"></i>
                            <span style="font-weight:600;">\${ct.courseName} CT</span>
                            <span style="font-size:0.8rem; background:rgba(255,0,0,0.1); padding:2px 6px; border-radius:4px;">\${new Date(ct.dateTime).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'})} | Rm \${ct.roomNo}</span>
                        </div>\`;
                    });
                    detailsHtml += \`</div>\`;
                }
                
                if (classes.length > 0) {
                    classes.sort((a,b) => a.startTime.localeCompare(b.startTime));
                    classes.forEach(c => {
                        detailsHtml += \`<div style="display:flex; gap:0.5rem; align-items:center; margin-bottom:0.5rem; color:var(--accent-blue);">
                            <i class="fa-solid fa-clock"></i>
                            <span style="font-weight:600; font-size:0.9rem;">\${c.startTime.substring(0,5)} - \${c.endTime.substring(0,5)}</span>
                            <span style="font-size:0.9rem;">\${c.courseName.split(' - ')[0]} (Rm \${c.roomNo})</span>
                        </div>\`;
                    });
                }
                
                detailsHost.innerHTML = detailsHtml;
            });
        });
    }
}
`;

appJs = appJs + '\n' + sep + studentScheduleCode;

fs.writeFileSync(appJsPath, appJs, 'utf8');
console.log('Successfully fixed app.js with correctly escaped calendar view!');
