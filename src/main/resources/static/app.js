// Глобальное состояние клиента
let authMode = 'login'; // 'login' или 'register'
let currentView = 'today'; // 'today' (US-03) или 'all' (US-02)
let loadedHabits = [];

const DAY_NAMES = {
    "1": "Пн", "2": "Вт", "3": "Ср", "4": "Чт", "5": "Пт", "6": "Сб", "7": "Вс"
};

// Инициализация при загрузке страницы
document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    document.getElementById('themeToggleBtn').addEventListener('click', toggleTheme);
    document.getElementById('logoutBtn').addEventListener('click', logout);

    const token = localStorage.getItem('ht_token');
    if (token) {
        showAppScreen();
    } else {
        showAuthScreen();
    }
});

// ================= УПРАВЛЕНИЕ ТЁМНОЙ ТЕМОЙ (US-06) =================
function initTheme() {
    const savedTheme = localStorage.getItem('ht_theme') || 'light';
    document.documentElement.setAttribute('data-theme', savedTheme);
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme');
    const next = current === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', next);
    localStorage.setItem('ht_theme', next);
}

// ================= ЕДИНЫЙ МОДУЛЬ ЗАПРОСОВ К API (TSK-05) =================
async function apiRequest(url, method = 'GET', body = null) {
    const headers = { 'Content-Type': 'application/json' };
    const token = localStorage.getItem('ht_token');
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const options = { method, headers };
    if (body) {
        options.body = JSON.stringify(body);
    }

    const response = await fetch(url, options);
    const data = await response.json().catch(() => ({}));

    if (!response.ok) {
        throw new Error(data.error || `Ошибка сервера (${response.status})`);
    }
    return data;
}

// ================= АВТОРИЗАЦИЯ И РЕГИСТРАЦИЯ (US-01) =================
function switchAuthMode(mode) {
    authMode = mode;
    document.getElementById('tabLogin').classList.toggle('active', mode === 'login');
    document.getElementById('tabRegister').classList.toggle('active', mode === 'register');
    document.getElementById('authTitle').textContent = mode === 'login' ? 'Вход в систему' : 'Регистрация аккаунта';
    document.getElementById('authSubmitBtn').textContent = mode === 'login' ? 'Войти' : 'Зарегистрироваться';
    hideAlert();
}

async function handleAuthSubmit(event) {
    event.preventDefault();
    hideAlert();

    const email = document.getElementById('emailInput').value.trim();
    const password = document.getElementById('passwordInput').value;

    const endpoint = authMode === 'login' ? '/api/auth/login' : '/api/auth/register';

    try {
        const result = await apiRequest(endpoint, 'POST', { email, password });
        localStorage.setItem('ht_token', result.token);
        localStorage.setItem('ht_email', result.email);
        showAppScreen();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

function logout() {
    localStorage.removeItem('ht_token');
    localStorage.removeItem('ht_email');
    showAuthScreen();
}

function showAuthScreen() {
    document.getElementById('authSection').classList.remove('hidden');
    document.getElementById('appSection').classList.add('hidden');
    document.getElementById('logoutBtn').classList.add('hidden');
    document.getElementById('userEmailDisplay').classList.add('hidden');
}

function showAppScreen() {
    document.getElementById('authSection').classList.add('hidden');
    document.getElementById('appSection').classList.remove('hidden');
    document.getElementById('logoutBtn').classList.remove('hidden');

    const emailEl = document.getElementById('userEmailDisplay');
    emailEl.textContent = localStorage.getItem('ht_email') || '';
    emailEl.classList.remove('hidden');

    loadHabitsAndStats();
}

// ================= СПИСОК ПРИВЫЧЕК И ОТМЕТКИ (US-02, US-03, US-04) =================
function switchHabitView(view) {
    currentView = view;
    document.getElementById('viewTodayBtn').classList.toggle('active', view === 'today');
    document.getElementById('viewAllBtn').classList.toggle('active', view === 'all');
    loadHabitsAndStats();
}

async function loadHabitsAndStats() {
    try {
        const endpoint = currentView === 'today' ? '/api/habits/today' : '/api/habits';
        loadedHabits = await apiRequest(endpoint, 'GET');
        renderHabitsList(loadedHabits);
        await loadWeeklyStats();
    } catch (err) {
        if (err.message.includes('авторизация') || err.message.includes('401')) {
            logout();
        } else {
            showAlert(err.message, 'error');
        }
    }
}

function renderHabitsList(habits) {
    const listEl = document.getElementById('habitsList');
    const emptyEl = document.getElementById('emptyState');
    listEl.innerHTML = '';

    if (!habits || habits.length === 0) {
        emptyEl.classList.remove('hidden');
        return;
    }
    emptyEl.classList.add('hidden');

    habits.forEach(habit => {
        const item = document.createElement('div');
        item.className = `habit-item ${habit.completedToday ? 'completed' : ''}`;

        const daysReadable = habit.daysOfWeek
            .split(',')
            .map(d => DAY_NAMES[d] || d)
            .join(', ');

        item.innerHTML = `
            <div class="habit-left">
                <input type="checkbox" class="habit-checkbox"
                       ${habit.completedToday ? 'checked' : ''}
                       onchange="toggleHabit(${habit.id})">
                <div>
                    <div class="habit-title">${escapeHtml(habit.title)}</div>
                    ${habit.description ? `<div class="habit-desc">${escapeHtml(habit.description)}</div>` : ''}
                    <div class="habit-days">Дни: ${daysReadable}</div>
                </div>
            </div>
            <div class="habit-actions">
                <button class="btn btn-outline" onclick="openHabitModal(${habit.id})">✏️</button>
                <button class="btn btn-danger" onclick="deleteHabit(${habit.id})">🗑️</button>
            </div>
        `;
        listEl.appendChild(item);
    });
}

// Отметка и отмена отметки выполнения привычки (US-04)
async function toggleHabit(id) {
    try {
        await apiRequest(`/api/habits/${id}/toggle`, 'POST');
        await loadHabitsAndStats();
    } catch (err) {
        showAlert(err.message, 'error');
        await loadHabitsAndStats();
    }
}

// Удаление привычки с подтверждением без перезагрузки страницы (US-02)
async function deleteHabit(id) {
    if (!confirm('Удалить эту привычку и историю её отметок?')) return;
    try {
        await apiRequest(`/api/habits/${id}`, 'DELETE');
        showAlert('Привычка удалена', 'success');
        await loadHabitsAndStats();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

// ================= МОДАЛЬНОЕ ОКНО: СОЗДАНИЕ И РЕДАКТИРОВАНИЕ (US-02) =================
function openHabitModal(habitId = null) {
    hideAlert();
    const modal = document.getElementById('habitModal');
    const titleEl = document.getElementById('modalTitle');
    const idInput = document.getElementById('habitIdInput');
    const titleInput = document.getElementById('habitTitleInput');
    const descInput = document.getElementById('habitDescInput');
    const dayCheckboxes = document.querySelectorAll('input[name="day"]');

    if (habitId) {
        const habit = loadedHabits.find(h => h.id === habitId);
        if (!habit) return;
        titleEl.textContent = 'Редактировать привычку';
        idInput.value = habit.id;
        titleInput.value = habit.title;
        descInput.value = habit.description || '';
        const selectedDays = habit.daysOfWeek.split(',');
        dayCheckboxes.forEach(cb => cb.checked = selectedDays.includes(cb.value));
    } else {
        titleEl.textContent = 'Новая привычка';
        idInput.value = '';
        titleInput.value = '';
        descInput.value = '';
        dayCheckboxes.forEach(cb => cb.checked = true);
    }

    modal.classList.remove('hidden');
}

function closeHabitModal() {
    document.getElementById('habitModal').classList.add('hidden');
}

async function handleHabitSave(event) {
    event.preventDefault();
    const id = document.getElementById('habitIdInput').value;
    const title = document.getElementById('habitTitleInput').value.trim();
    const description = document.getElementById('habitDescInput').value.trim();

    const selectedDays = Array.from(document.querySelectorAll('input[name="day"]:checked'))
        .map(cb => cb.value)
        .join(',');

    if (!title) {
        showAlert('Название привычки не может быть пустым', 'error');
        return;
    }
    if (!selectedDays) {
        showAlert('Выберите хотя бы один день недели', 'error');
        return;
    }

    try {
        if (id) {
            await apiRequest(`/api/habits/${id}`, 'PUT', { title, description, daysOfWeek: selectedDays });
            showAlert('Привычка обновлена', 'success');
        } else {
            await apiRequest('/api/habits', 'POST', { title, description, daysOfWeek: selectedDays });
            showAlert('Привычка добавлена', 'success');
        }
        closeHabitModal();
        await loadHabitsAndStats();
    } catch (err) {
        showAlert(err.message, 'error');
    }
}

// ================= СТАТИСТИКА ЗА НЕДЕЛЮ (US-05) =================
async function loadWeeklyStats() {
    const stats = await apiRequest('/api/habits/stats/week', 'GET');
    const container = document.getElementById('weeklyStatsContainer');
    container.innerHTML = '';

    stats.forEach(day => {
        const percent = day.planned > 0 ? Math.min(100, Math.round((day.completed / day.planned) * 100)) : 0;
        const shortDate = day.date.slice(5); // MM-DD

        const el = document.createElement('div');
        el.className = 'stat-day';
        el.innerHTML = `
            <div class="stat-day-label">${day.dayLabel}</div>
            <div class="stat-day-date">${shortDate}</div>
            <div class="stat-bar-bg">
                <div class="stat-bar-fill" style="width: ${percent}%"></div>
            </div>
            <div class="stat-count">${day.completed} / ${day.planned}</div>
        `;
        container.appendChild(el);
    });
}

// ================= ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ =================
function showAlert(message, type = 'error') {
    const box = document.getElementById('alertBox');
    box.textContent = message;
    box.className = `alert ${type === 'error' ? 'alert-error' : 'alert-success'}`;
    box.classList.remove('hidden');
    setTimeout(hideAlert, 4000);
}

function hideAlert() {
    document.getElementById('alertBox').classList.add('hidden');
}

function escapeHtml(str) {
    return str.replace(/[&<>"']/g, m => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[m]));
}