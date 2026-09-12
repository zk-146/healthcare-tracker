import { useEffect, useRef, useState, type FormEvent } from 'react';
import { type ApiClient } from '../api/client';
import { importFitbitCsv } from '../api/endpoints';
import type { CsvImportResponse } from '../api/types';
import { messageFor } from '../lib/apiMessage';
import { ErrorNote } from '../ui/ErrorNote';

interface ImportCsvDialogProps {
  api: ApiClient;
  onClose(): void;
  /** Fires once, after a successful upload, so the caller can refetch the list. */
  onImported(): void;
}

/**
 * Uploads a Fitbit `dailyActivity_merged.csv` export. Row-level problems (duplicates,
 * malformed rows) come back as a 200 with counts and a capped error list, not a
 * rejection — only a network/validation failure on the upload itself throws.
 */
export function ImportCsvDialog({ api, onClose, onImported }: ImportCsvDialogProps) {
  const [file, setFile] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CsvImportResponse | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    fileInputRef.current?.focus();
  }, []);

  useEffect(() => {
    function handleKeyDown(event: globalThis.KeyboardEvent): void {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  async function handleSubmit(event: FormEvent): Promise<void> {
    event.preventDefault();
    if (file === null) {
      setError('Choose a CSV file first');
      return;
    }

    setBusy(true);
    setError(null);
    try {
      const response = await importFitbitCsv(api, file);
      setResult(response);
      if (response.imported > 0) {
        onImported();
      }
    } catch (cause: unknown) {
      setError(messageFor(cause));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="overlay">
      <div className="overlay-panel" role="dialog" aria-modal="true" aria-label="Import Fitbit CSV">
        <form onSubmit={(event) => void handleSubmit(event)} noValidate>
          <header className="overlay-header">
            <h2 className="overlay-title">Import Fitbit CSV</h2>
            <button type="button" className="link-button" onClick={onClose} disabled={busy}>
              Close
            </button>
          </header>

          <p className="empty-note">
            Upload a Fitbit <code>dailyActivity_merged.csv</code> export (e.g. the Kaggle
            "FitBit Fitness Tracker Data" dataset). Rows already imported are skipped
            automatically.
          </p>

          {error !== null && <ErrorNote message={error} />}

          {result !== null && (
            <p className="empty-note">
              Imported {result.imported} of {result.totalRows} rows
              {result.duplicatesSkipped > 0 && `, ${result.duplicatesSkipped} already imported`}
              {result.failed > 0 && `, ${result.failed} failed`}.
              {result.errors.length > 0 && (
                <>
                  {' '}
                  <br />
                  {result.errors.join(' ')}
                </>
              )}
            </p>
          )}

          <div className="field">
            <label className="field-label" htmlFor="csvFile">
              CSV file
            </label>
            <input
              id="csvFile"
              ref={fileInputRef}
              type="file"
              accept=".csv,text/csv"
              disabled={busy}
              onChange={(event) => {
                setFile(event.target.files?.[0] ?? null);
                setResult(null);
                setError(null);
              }}
            />
          </div>

          <button type="submit" className="primary-button" disabled={busy || file === null}>
            {busy ? 'Importing…' : 'Import'}
          </button>
        </form>
      </div>
    </div>
  );
}
