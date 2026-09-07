interface ErrorNoteProps {
  message: string;
}

export function ErrorNote({ message }: ErrorNoteProps) {
  return (
    <p role="alert" className="error-note">
      {message}
    </p>
  );
}
