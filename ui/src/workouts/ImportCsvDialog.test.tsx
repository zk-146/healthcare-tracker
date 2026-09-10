import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { ApiClient } from '../api/client';
import type { CsvImportResponse } from '../api/types';
import { ImportCsvDialog } from './ImportCsvDialog';

function stubApi(postForm: ReturnType<typeof vi.fn>): ApiClient {
  return {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    del: vi.fn(),
    postForm,
  } as unknown as ApiClient;
}

function csvFile(name = 'dailyActivity_merged.csv'): File {
  return new File(['Id,ActivityDate\n1,4/1/2026'], name, { type: 'text/csv' });
}

const response: CsvImportResponse = {
  fileName: 'dailyActivity_merged.csv',
  totalRows: 10,
  imported: 8,
  duplicatesSkipped: 2,
  failed: 0,
  errors: [],
};

describe('ImportCsvDialog', () => {
  it('disables the Import button until a file is chosen', async () => {
    const postForm = vi.fn();
    const user = userEvent.setup();
    render(<ImportCsvDialog api={stubApi(postForm)} onClose={vi.fn()} onImported={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Import' })).toBeDisabled();

    await user.upload(screen.getByLabelText(/csv file/i), csvFile());

    expect(screen.getByRole('button', { name: 'Import' })).toBeEnabled();
  });

  it('uploads the chosen file and reports the import counts', async () => {
    const postForm = vi.fn().mockResolvedValue(response);
    const onImported = vi.fn();
    const user = userEvent.setup();
    render(<ImportCsvDialog api={stubApi(postForm)} onClose={vi.fn()} onImported={onImported} />);

    await user.upload(screen.getByLabelText(/csv file/i), csvFile());
    await user.click(screen.getByRole('button', { name: 'Import' }));

    await waitFor(() => expect(postForm).toHaveBeenCalledTimes(1));
    const [path, form] = postForm.mock.calls[0] as [string, FormData];
    expect(path).toBe('/api/v1/activities/import/fitbit');
    expect((form.get('file') as File).name).toBe('dailyActivity_merged.csv');

    expect(await screen.findByText(/imported 8 of 10 rows/i)).toBeInTheDocument();
    expect(screen.getByText(/2 already imported/i)).toBeInTheDocument();
    expect(onImported).toHaveBeenCalled();
  });

  it('does not report success to the parent when nothing new was imported', async () => {
    const postForm = vi.fn().mockResolvedValue({ ...response, imported: 0, duplicatesSkipped: 10 });
    const onImported = vi.fn();
    const user = userEvent.setup();
    render(<ImportCsvDialog api={stubApi(postForm)} onClose={vi.fn()} onImported={onImported} />);

    await user.upload(screen.getByLabelText(/csv file/i), csvFile());
    await user.click(screen.getByRole('button', { name: 'Import' }));

    expect(await screen.findByText(/imported 0 of 10 rows/i)).toBeInTheDocument();
    expect(onImported).not.toHaveBeenCalled();
  });

  it('surfaces row-level errors from a partially failed import', async () => {
    const postForm = vi.fn().mockResolvedValue({
      ...response,
      imported: 7,
      failed: 1,
      errors: ['Row 4: Missing value for Calories'],
    });
    const user = userEvent.setup();
    render(<ImportCsvDialog api={stubApi(postForm)} onClose={vi.fn()} onImported={vi.fn()} />);

    await user.upload(screen.getByLabelText(/csv file/i), csvFile());
    await user.click(screen.getByRole('button', { name: 'Import' }));

    expect(await screen.findByText(/1 failed/i)).toBeInTheDocument();
    expect(screen.getByText(/missing value for calories/i)).toBeInTheDocument();
  });

  it('shows an error banner when the upload itself fails', async () => {
    const postForm = vi.fn().mockRejectedValue(new Error('Could not reach the server.'));
    const user = userEvent.setup();
    render(<ImportCsvDialog api={stubApi(postForm)} onClose={vi.fn()} onImported={vi.fn()} />);

    await user.upload(screen.getByLabelText(/csv file/i), csvFile());
    await user.click(screen.getByRole('button', { name: 'Import' }));

    expect(await screen.findByText(/could not reach the server/i)).toBeInTheDocument();
  });

  it('closes on Escape and on the Close button', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<ImportCsvDialog api={stubApi(vi.fn())} onClose={onClose} onImported={vi.fn()} />);

    await user.click(screen.getByRole('button', { name: 'Close' }));
    expect(onClose).toHaveBeenCalledTimes(1);

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(2);
  });
});
