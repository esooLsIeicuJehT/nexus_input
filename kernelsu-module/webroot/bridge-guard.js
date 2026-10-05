(() => {
  if (!window.ksu || typeof window.ksu.exec !== 'function') return;

  const rawExec = window.ksu.exec.bind(window.ksu);
  let tail = Promise.resolve();

  function runQueued(command, options, callbackName) {
    return new Promise((resolve) => {
      const originalCallback = window[callbackName];
      if (typeof originalCallback !== 'function') {
        resolve();
        return;
      }

      let finished = false;
      const finish = () => {
        if (finished) return;
        finished = true;
        clearTimeout(queueGuard);
        resolve();
      };

      const queueGuard = setTimeout(finish, 185000);
      window[callbackName] = (...args) => {
        try {
          originalCallback(...args);
        } finally {
          finish();
        }
      };

      try {
        rawExec(command, options, callbackName);
      } catch (error) {
        window[callbackName] = originalCallback;
        finish();
        throw error;
      }
    });
  }

  window.ksu.exec = (command, options, callbackName) => {
    const queued = () => runQueued(command, options, callbackName);
    tail = tail.then(queued, queued).catch(() => undefined);
  };
})();
