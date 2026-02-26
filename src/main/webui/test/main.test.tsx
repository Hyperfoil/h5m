import { App } from '@app/App';
import { cleanup, render } from '@testing-library/react';
import { describe, it } from 'vitest';

describe('<App />', () => {
  it('renders', () => {
    window.history.pushState({}, 'Home', '/');

    render(<App />);
    cleanup();
  });
});
