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

document.querySelectorAll('.rep-range-inputs').forEach((fields) => {
  const minReps = fields.querySelector('input[name="minReps"]');
  const maxReps = fields.querySelector('input[name="maxReps"]');
  const validateRange = () => {
    const invalidRange = minReps.value !== '' && maxReps.value !== '' && Number(minReps.value) > Number(maxReps.value);
    maxReps.setCustomValidity(invalidRange ? 'Maximum reps must be at least the minimum reps.' : '');
  };

  minReps.addEventListener('input', validateRange);
  maxReps.addEventListener('input', validateRange);
  const form = fields.closest('form');
  form.addEventListener('submit', (event) => {
    validateRange();
    if (maxReps.validationMessage) {
      event.preventDefault();
      maxReps.reportValidity();
    }
  });
});

const formatClock = (totalSeconds) => {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const pad = (value) => String(value).padStart(2, '0');
  return hours > 0 ? `${pad(hours)}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`;
};

const workoutClock = document.querySelector('[data-workout-clock]');
if (workoutClock) {
  const display = workoutClock.querySelector('[data-workout-clock-value]');
  const elapsedAtLoad = Number(workoutClock.dataset.elapsedSeconds);
  const isRunning = workoutClock.dataset.status === 'IN_PROGRESS';
  const loadedAt = Date.now();
  const updateWorkoutClock = () => {
    const elapsed = elapsedAtLoad + (isRunning ? Math.floor((Date.now() - loadedAt) / 1000) : 0);
    display.textContent = formatClock(elapsed);
  };
  updateWorkoutClock();
  if (isRunning) window.setInterval(updateWorkoutClock, 1000);
}

const restTimer = document.querySelector('[data-rest-timer]');
if (restTimer) {
  const workoutId = restTimer.dataset.workoutId;
  const storageKey = `workout-rest-timer:${workoutId}`;
  const url = new URL(window.location.href);
  const remainingMillis = Number(restTimer.dataset.restRemainingMillis);
  if (url.searchParams.has('restStartedAt')) {
    if (remainingMillis > 0) {
      localStorage.setItem(storageKey, JSON.stringify({ expiresAt: Date.now() + remainingMillis }));
    } else {
      localStorage.removeItem(storageKey);
    }
    url.searchParams.delete('restSeconds');
    url.searchParams.delete('restStartedAt');
    window.history.replaceState({}, '', url);
  }

  const countdown = restTimer.querySelector('[data-rest-countdown]');
  const cancelButton = restTimer.querySelector('[data-cancel-rest]');
  const updateRestTimer = () => {
    const timer = JSON.parse(localStorage.getItem(storageKey) || 'null');
    if (!timer) {
      restTimer.hidden = true;
      return;
    }
    const remaining = Math.ceil((timer.expiresAt - Date.now()) / 1000);
    if (remaining <= 0) {
      localStorage.removeItem(storageKey);
      restTimer.hidden = true;
      return;
    }
    restTimer.hidden = false;
    countdown.textContent = formatClock(remaining);
  };
  cancelButton.addEventListener('click', () => {
    localStorage.removeItem(storageKey);
    restTimer.hidden = true;
  });
  updateRestTimer();
  window.setInterval(updateRestTimer, 250);
}

document.querySelectorAll('[data-finish-workout]').forEach((form) => {
  form.addEventListener('submit', () => {
    const workoutId = workoutClock?.dataset.workoutId;
    if (workoutId) localStorage.removeItem(`workout-rest-timer:${workoutId}`);
  });
});

const initializeExercisePicker = (picker) => {
  if (picker.dataset.initialized) return;
  picker.dataset.initialized = 'true';
  const search = picker.querySelector('.exercise-picker-search');
  const selected = picker.querySelector('input[name="exerciseId"], input[data-exercise-id]');
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

  const updateExerciseDetails = (exercise) => {
    const row = picker.closest('[data-routine-exercise]');
    const details = row?.querySelector('[data-exercise-details]');
    if (!details) return;
    details.querySelector('[data-exercise-muscles]').textContent = exercise?.primaryMuscles || 'Muscles not listed';
    details.querySelector('[data-exercise-equipment]').textContent = exercise?.equipment || 'Equipment not listed';
    details.querySelector('[data-exercise-instructions]').textContent =
      exercise?.instructions || 'No instructions are available for this exercise.';
    const image = details.querySelector('[data-exercise-image]');
    if (exercise?.sourceId) {
      image.src = `/exercise-images/${encodeURIComponent(exercise.sourceId)}/0`;
      image.hidden = false;
      image.style.display = '';
    } else {
      image.removeAttribute('src');
      image.hidden = true;
    }
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
        signal: activeRequest.signal,
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
        option.dataset.primaryMuscles = exercise.primary_muscles || '';
        option.dataset.equipment = exercise.equipment || '';
        option.dataset.instructions = exercise.instructions || '';
        option.dataset.sourceId = exercise.source_id || '';
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
    updateExerciseDetails(null);
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
    updateExerciseDetails(option.dataset);
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
};

document.querySelectorAll('[data-exercise-picker]').forEach(initializeExercisePicker);

document.addEventListener('click', (event) => {
  document.querySelectorAll('[data-exercise-picker]').forEach((picker) => {
    if (!picker.contains(event.target)) {
      picker.querySelector('.exercise-picker-results').hidden = true;
      picker.querySelector('.exercise-picker-search').setAttribute('aria-expanded', 'false');
    }
  });
});

const routineDraft = document.querySelector('[data-routine-draft]');
if (routineDraft) {
  const daysContainer = routineDraft.querySelector('[data-routine-days]');
  const exerciseTemplate = document.querySelector('#routine-builder-exercise-template');
  const dayTemplate = document.querySelector('#routine-builder-day-template');

  const renumberDraft = () => {
    [...daysContainer.querySelectorAll('[data-routine-day]')].forEach((day, dayIndex) => {
      const dayName = day.querySelector('.routine-draft-day-heading input');
      dayName.name = `days[${dayIndex}].name`;
      [...day.querySelectorAll('[data-routine-exercise]')].forEach((exercise, exerciseIndex) => {
        const inputs = exercise.querySelectorAll('input');
        const prefix = `days[${dayIndex}].exercises[${exerciseIndex}]`;
        inputs[0].name = `${prefix}.exerciseName`;
        inputs[1].name = `${prefix}.exerciseId`;
        inputs[1].dataset.exerciseId = 'true';
        inputs[2].name = `${prefix}.sets`;
        inputs[3].name = `${prefix}.minReps`;
        inputs[4].name = `${prefix}.maxReps`;
        inputs[5].name = `${prefix}.restSeconds`;
        initializeExercisePicker(exercise.querySelector('[data-exercise-picker]'));
      });
    });
  };

  const addExercise = (day) => {
    const exercise = exerciseTemplate.content.firstElementChild.cloneNode(true);
    day.querySelector('[data-routine-exercises]').append(exercise);
    renumberDraft();
    exercise.querySelector('.exercise-picker-search').focus();
  };

  routineDraft.addEventListener('click', (event) => {
    const addDayButton = event.target.closest('[data-add-day]');
    if (addDayButton) {
      const day = dayTemplate.content.firstElementChild.cloneNode(true);
      const index = daysContainer.querySelectorAll('[data-routine-day]').length + 1;
      day.querySelector('.routine-draft-day-heading input').value = `Day ${index}`;
      daysContainer.append(day);
      addExercise(day);
      day.querySelector('.routine-draft-day-heading input').focus();
      return;
    }

    const addExerciseButton = event.target.closest('[data-add-exercise]');
    if (addExerciseButton) {
      addExercise(addExerciseButton.closest('[data-routine-day]'));
      return;
    }

    const removeExerciseButton = event.target.closest('[data-remove-exercise]');
    if (removeExerciseButton) {
      removeExerciseButton.closest('[data-routine-exercise]').remove();
      renumberDraft();
      return;
    }

    const removeDayButton = event.target.closest('[data-remove-day]');
    if (removeDayButton) {
      removeDayButton.closest('[data-routine-day]').remove();
      renumberDraft();
    }
  });

  renumberDraft();
}

document.querySelectorAll('[data-ai-submit]').forEach((button) => {
  const form = button.closest('[data-ai-progress-form]');
  if (!form) return;

  form.addEventListener('submit', (event) => {
    if (event.defaultPrevented || event.submitter !== button) return;
    const idleLabel = button.querySelector('[data-ai-idle-label]');
    const pendingLabel = button.querySelector('[data-ai-pending-label]');
    const progress = form.querySelector('[data-ai-progress]');
    button.disabled = true;
    if (idleLabel) idleLabel.hidden = true;
    if (pendingLabel) pendingLabel.hidden = false;
    if (progress) progress.hidden = false;
    form.setAttribute('aria-busy', 'true');
  });
});
