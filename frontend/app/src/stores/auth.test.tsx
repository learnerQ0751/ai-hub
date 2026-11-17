import { resetMockSession } from '@/api/session';
import { AuthProvider } from '@/stores/AuthProvider';
import { useAuth } from '@/stores/auth';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it } from 'vitest';

function AuthProbe() {
  const { status, session, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="user">{session?.username ?? '-'}</span>
      <button onClick={() => void login('admin', 'admin123')}>do-login</button>
      <button onClick={() => void logout()}>do-logout</button>
    </div>
  );
}

beforeEach(() => {
  resetMockSession();
});

describe('登录状态机', () => {
  it('checking → anonymous → authenticated → anonymous', async () => {
    render(
      <AuthProvider>
        <AuthProbe />
      </AuthProvider>,
    );

    expect(screen.getByTestId('status').textContent).toBe('checking');
    await screen.findByText('anonymous');

    act(() => {
      fireEvent.click(screen.getByText('do-login'));
    });
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('authenticated'));
    expect(screen.getByTestId('user').textContent).toBe('admin');

    act(() => {
      fireEvent.click(screen.getByText('do-logout'));
    });
    await waitFor(() => expect(screen.getByTestId('status').textContent).toBe('anonymous'));
    expect(screen.getByTestId('user').textContent).toBe('-');
  });
});
