if (!document.body.dataset.theme) {
  document.body.dataset.theme = localStorage.getItem('appearance') || 'system';
} else {
  localStorage.setItem('appearance', document.body.dataset.theme);
}

const mobileMenuButton = document.querySelector('.menu-toggle');
const sidebar = document.querySelector('.sidebar');
const closeMobileMenu = () => {
  sidebar?.classList.remove('menu-open');
  mobileMenuButton?.setAttribute('aria-expanded', 'false');
  mobileMenuButton?.setAttribute('aria-label', 'Open navigation menu');
};

mobileMenuButton?.addEventListener('click', () => {
  const isOpen = sidebar.classList.toggle('menu-open');
  mobileMenuButton.setAttribute('aria-expanded', String(isOpen));
  mobileMenuButton.setAttribute('aria-label', isOpen ? 'Close navigation menu' : 'Open navigation menu');
  if (isOpen) sidebar.querySelector('nav a')?.focus();
});

sidebar?.querySelectorAll('nav a').forEach((link) => link.addEventListener('click', closeMobileMenu));
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && sidebar?.classList.contains('menu-open')) {
    closeMobileMenu();
    mobileMenuButton?.focus();
  }
});
document.addEventListener('click', (event) => {
  if (sidebar?.classList.contains('menu-open') && !sidebar.contains(event.target)) closeMobileMenu();
});

document.querySelector('#theme-mode')?.addEventListener('change', (event) => {
  const mode = event.target.value.toLowerCase();
  document.body.dataset.theme = mode;
  localStorage.setItem('appearance', mode);
});

document.addEventListener('click', (event) => {
  const button = event.target.closest('[data-seconds]');
  if (!button) return;
  if (window.restInterval) clearInterval(window.restInterval);
  const label = button.querySelector('span');
  const total = Number(button.dataset.seconds) || 0;
  if (!total) return;
  let left = total;
  button.classList.add('running');
  const tick = () => {
    label.textContent = `${left}s`;
    if (left <= 0) {
      clearInterval(window.restInterval);
      button.classList.remove('running');
      label.textContent = 'Done';
      return;
    }
    left -= 1;
  };
  tick();
  window.restInterval = setInterval(tick, 1000);
});

document.querySelectorAll('[data-exercise-picker]').forEach((picker) => {
  const search = picker.querySelector('.exercise-picker-search');
  const selected = picker.querySelector('input[name="exerciseId"]');
  const results = picker.querySelector('.exercise-picker-results');
  let debounce;
  let activeRequest;

  const showMessage = (message) => {
    results.replaceChildren();
    const note = document.createElement('div');
    note.className = 'exercise-picker-message';
    note.textContent = message;
    results.append(note);
  };

  const searchExercises = async () => {
    const query = search.value.trim();
    if (query.length < 2) {
      showMessage('Type at least 2 characters to search.');
      results.hidden = false;
      search.setAttribute('aria-expanded', 'true');
      return;
    }

    activeRequest?.abort();
    activeRequest = new AbortController();
    showMessage('Searching…');
    results.hidden = false;
    search.setAttribute('aria-expanded', 'true');
    try {
      const response = await fetch(`/api/exercises/search?q=${encodeURIComponent(query)}`, {
        headers: { Accept: 'application/json' },
        signal: activeRequest.signal
      });
      if (!response.ok) throw new Error('Exercise search failed');
      const exercises = await response.json();
      if (search.value.trim() !== query) return;
      results.replaceChildren();
      if (!exercises.length) {
        showMessage('No matching exercises found.');
        return;
      }
      exercises.forEach((exercise) => {
        const option = document.createElement('button');
        option.type = 'button';
        option.className = 'exercise-picker-option';
        option.setAttribute('role', 'option');
        option.dataset.exerciseId = exercise.id;
        option.textContent = exercise.name;
        results.append(option);
      });
    } catch (error) {
      if (error.name !== 'AbortError') showMessage('Search failed. Please try again.');
    }
  };

  search.addEventListener('focus', searchExercises);
  search.addEventListener('input', () => {
    selected.value = '';
    clearTimeout(debounce);
    debounce = setTimeout(searchExercises, 180);
  });
  search.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
      results.hidden = true;
      search.setAttribute('aria-expanded', 'false');
    } else if (event.key === 'ArrowDown') {
      const first = results.querySelector('.exercise-picker-option');
      if (first) {
        event.preventDefault();
        first.focus();
      }
    }
  });
  picker.addEventListener('click', (event) => {
    const option = event.target.closest('.exercise-picker-option');
    if (!option) return;
    search.value = option.textContent.trim();
    selected.value = option.dataset.exerciseId;
    results.hidden = true;
    search.setAttribute('aria-expanded', 'false');
  });
  picker.closest('form').addEventListener('submit', (event) => {
    if (!selected.value) {
      event.preventDefault();
      search.setCustomValidity('Search for and select an exercise.');
      search.reportValidity();
      search.addEventListener('input', () => search.setCustomValidity(''), { once: true });
    }
  });
});

document.addEventListener('click', (event) => {
  document.querySelectorAll('[data-exercise-picker]').forEach((picker) => {
    if (!picker.contains(event.target)) {
      picker.querySelector('.exercise-picker-results').hidden = true;
      picker.querySelector('.exercise-picker-search').setAttribute('aria-expanded', 'false');
    }
  });
});
