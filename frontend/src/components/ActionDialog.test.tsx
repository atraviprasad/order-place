import { describe, expect, it, jest } from '@jest/globals';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ActionDialog } from './ActionDialog';

function setup(props: Partial<React.ComponentProps<typeof ActionDialog>> = {}) {
  const onConfirm = jest.fn<(reason: string) => void>();
  const onClose = jest.fn<() => void>();
  render(<ActionDialog open title="Reject order?" text="This cannot be undone." confirmLabel="Reject" busy={false} error={null} onConfirm={onConfirm} onClose={onClose} {...props} />);
  return { onConfirm, onClose, user: userEvent.setup() };
}

describe('ActionDialog', () => {
  it('shows nothing when closed', () => {
    setup({ open: false });
    expect(screen.queryByText('Reject order?')).not.toBeInTheDocument();
  });

  it('shows the title and text and confirms with an empty reason when no reason field is set', async () => {
    const { onConfirm, user } = setup();
    expect(screen.getByText('Reject order?')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Reject' }));
    expect(onConfirm).toHaveBeenCalledWith('');
  });

  it('keeps Confirm disabled until a required reason is typed, then passes the trimmed text', async () => {
    const { onConfirm, user } = setup({ reasonLabel: 'Reason', reasonRequired: true });
    const confirm = screen.getByRole('button', { name: 'Reject' });
    expect(confirm).toBeDisabled();
    await user.type(screen.getByRole('textbox', { name: /reason/i }), '  Out of stock ');
    expect(confirm).toBeEnabled();
    await user.click(confirm);
    expect(onConfirm).toHaveBeenCalledWith('Out of stock');
  });

  it('disables both buttons while busy and shows the error text', () => {
    setup({ busy: true, error: 'Order already approved' });
    expect(screen.getByRole('button', { name: 'Reject' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Back' })).toBeDisabled();
    expect(screen.getByRole('alert')).toHaveTextContent('Order already approved');
  });

  it('calls onClose from Back', async () => {
    const { onClose, user } = setup();
    await user.click(screen.getByRole('button', { name: 'Back' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
