import { useEffect, useState } from "react";
import {
  Link,
  Navigate,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { api, apiError } from "../lib/api";
import { useAuth, useToast } from "../lib/AppContext";
import { useSeo } from "../lib/seo";
import { Field, PasswordInput } from "../components/Bits";
import { mobileError, mobileInput } from "../lib/forms";

function Shell({ title, children }) {
  return (
    <div className="container section" style={{ maxWidth: 460 }}>
      <h1 className="display-font h2 mb-4">{title}</h1>
      {children}
    </div>
  );
}

export function passwordHint(p) {
  const m = [];
  if (p.length < 10) m.push("at least 10 characters");
  if (!/[a-z]/.test(p)) m.push("a lowercase letter");
  if (!/[A-Z]/.test(p)) m.push("an uppercase letter");
  if (!/[0-9]/.test(p)) m.push("a number");
  return m;
}

export function Login() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const [f, setF] = useState({ email: "", password: "" });
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  useSeo({ title: "Log in", noindex: true });
  const from = loc.state?.from || "/account";
  if (user)
    return (
      <Navigate
        to={user.staff && from === "/account" ? "/admin" : from}
        replace
      />
    );
  async function submit(e) {
    e.preventDefault();
    setErr("");
    setBusy(true);
    try {
      const r = await api.post("/auth/login", f);
      login(r.data);
      nav(r.data.user.staff && from === "/account" ? "/admin" : from, {
        replace: true,
      });
    } catch (x) {
      setErr(apiError(x).message);
    } finally {
      setBusy(false);
    }
  }
  return (
    <Shell title="Log in">
      <form onSubmit={submit}>
        {err && (
          <div className="alert alert-danger" role="alert">
            {err}
          </div>
        )}
        <Field label="Email" id="l-e" required>
          <input
            id="l-e"
            type="email"
            required
            autoComplete="email"
            className="form-control"
            value={f.email}
            onChange={(e) => setF({ ...f, email: e.target.value })}
          />
        </Field>
        <Field label="Password" id="l-p" required>
          <PasswordInput
            id="l-p"
            required
            autoComplete="current-password"
            className="form-control"
            value={f.password}
            onChange={(e) => setF({ ...f, password: e.target.value })}
          />
        </Field>
        <button className="btn btn-tf w-100" disabled={busy}>
          {busy ? "Logging in…" : "Log in"}
        </button>
        <div className="d-flex justify-content-between mt-3 small">
          <Link to="/forgot-password">Forgot password?</Link>
          <Link to="/register" state={{ from }}>
            Create account
          </Link>
        </div>
      </form>
    </Shell>
  );
}

export function Register() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const loc = useLocation();
  const toast = useToast();
  const [f, setF] = useState({
    fullName: "",
    email: "",
    phone: "",
    password: "",
    marketingConsent: false,
  });
  const [errors, setErrors] = useState({});
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  useSeo({ title: "Create account", noindex: true });
  if (user) return <Navigate to="/account" replace />;
  const miss = passwordHint(f.password);
  async function submit(e) {
    e.preventDefault();
    setErr("");
    setErrors({});
    const v = {};
    if (f.fullName.trim().length < 2) v.fullName = "Enter your full name.";
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email.trim())) v.email = "Enter a valid email address.";
    const pe = mobileError(f.phone);
    if (pe) v.phone = pe;
    if (miss.length) v.password = `Password needs ${miss.join(", ")}.`;
    if (Object.keys(v).length) {
      setErrors(v);
      setErr("Please correct the highlighted fields.");
      return;
    }
    setBusy(true);
    try {
      const r = await api.post("/auth/register", f);
      login(r.data);
      toast.push("Account created. Check your email to verify it.");
      nav(loc.state?.from || "/account", { replace: true });
    } catch (x) {
      const er = apiError(x);
      setErrors(er.fields);
      setErr(er.message);
    } finally {
      setBusy(false);
    }
  }
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  return (
    <Shell title="Create account">
      <form onSubmit={submit} noValidate>
        {err && (
          <div className="alert alert-danger" role="alert">
            {err}
          </div>
        )}
        <Field label="Full name" id="r-n" error={errors.fullName} required>
          <input
            id="r-n"
            className={`form-control ${errors.fullName ? "is-invalid" : ""}`}
            autoComplete="name"
            value={f.fullName}
            onChange={set("fullName")}
          />
        </Field>
        <Field label="Email" id="r-e" error={errors.email} required>
          <input
            id="r-e"
            type="email"
            className={`form-control ${errors.email ? "is-invalid" : ""}`}
            autoComplete="email"
            value={f.email}
            onChange={set("email")}
          />
        </Field>

        <Field label="Mobile number" id="r-p" error={errors.phone} required>
          <input
            id="r-p"
            type="tel"
            required
            aria-required="true"
            placeholder="10-digit mobile number"
            className={`form-control ${errors.phone ? "is-invalid" : ""}`}
            autoComplete="tel-national"
            inputMode="numeric"
            value={f.phone}
            onChange={(e) => setF({ ...f, phone: mobileInput(e.target.value) })}
          />
        </Field>
        <Field label="Password" id="r-pw" error={errors.password} required>
          <PasswordInput
            id="r-pw"
            className={`form-control ${errors.password ? "is-invalid" : ""}`}
            autoComplete="new-password"
            value={f.password}
            onChange={set("password")}
          />
          {f.password && miss.length > 0 && (
            <div className="small text-muted mt-1">
              Needs {miss.join(", ")}.
            </div>
          )}
        </Field>
        <div className="form-check mb-3">
          <input
            id="r-m"
            className="form-check-input"
            type="checkbox"
            checked={f.marketingConsent}
            onChange={(e) => setF({ ...f, marketingConsent: e.target.checked })}
          />
          <label htmlFor="r-m" className="form-check-label small">
            Send me offers and new arrivals (optional)
          </label>
        </div>
        <button className="btn btn-tf w-100" disabled={busy}>
          {busy ? "Creating…" : "Create account"}
        </button>
        <div className="mt-3 small">
          Already have an account? <Link to="/login">Log in</Link>
        </div>
      </form>
    </Shell>
  );
}

export function Forgot() {
  const [email, setEmail] = useState("");
  const [msg, setMsg] = useState("");
  const [err, setErr] = useState("");
  useSeo({ title: "Forgot password", noindex: true });
  async function submit(e) {
    e.preventDefault();
    setErr("");
    try {
      setMsg((await api.post("/auth/forgot-password", { email })).data.message);
    } catch (x) {
      setErr(apiError(x).message);
    }
  }
  return (
    <Shell title="Forgot password">
      {msg ? (
        <div className="alert alert-success">{msg}</div>
      ) : (
        <form onSubmit={submit}>
          {err && <div className="alert alert-danger">{err}</div>}
          <Field label="Email" id="f-e">
            <input
              id="f-e"
              type="email"
              required
              className="form-control"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </Field>
          <button className="btn btn-tf w-100">Send reset link</button>
        </form>
      )}
      <div className="mt-3 small">
        <Link to="/login">Back to log in</Link>
      </div>
    </Shell>
  );
}

export function Reset() {
  const [sp] = useSearchParams();
  const nav = useNavigate();
  const [pw, setPw] = useState("");
  const [err, setErr] = useState("");
  const [ok, setOk] = useState(false);
  useSeo({ title: "Reset password", noindex: true });
  const miss = passwordHint(pw);
  async function submit(e) {
    e.preventDefault();
    setErr("");
    if (miss.length) {
      setErr(`Password needs ${miss.join(", ")}.`);
      return;
    }
    try {
      await api.post("/auth/reset-password", {
        token: sp.get("token"),
        password: pw,
      });
      setOk(true);
      setTimeout(() => nav("/login"), 1800);
    } catch (x) {
      setErr(apiError(x).message);
    }
  }
  return (
    <Shell title="Choose a new password">
      {ok ? (
        <div className="alert alert-success">
          Password updated. Redirecting to log in…
        </div>
      ) : (
        <form onSubmit={submit}>
          {err && <div className="alert alert-danger">{err}</div>}
          <Field label="New password" id="rp" required>
            <PasswordInput
              id="rp"
              autoComplete="new-password"
              className="form-control"
              value={pw}
              onChange={(e) => setPw(e.target.value)}
            />
          </Field>
          <button className="btn btn-tf w-100">Update password</button>
        </form>
      )}
    </Shell>
  );
}

export function VerifyEmail() {
  const [sp] = useSearchParams();
  const { setUser, user } = useAuth();
  const [state, setState] = useState({ busy: true, msg: "" });
  useSeo({ title: "Verify email", noindex: true });
  useEffect(() => {
    api
      .post("/auth/verify-email", { token: sp.get("token") })
      .then((r) => {
        setState({ busy: false, msg: r.data.message, ok: true });
        if (user) setUser({ ...user, emailVerified: true });
      })
      .catch((e) =>
        setState({ busy: false, msg: apiError(e).message, ok: false }),
      );
  }, []); // eslint-disable-line
  return (
    <Shell title="Email verification">
      {state.busy ? (
        "Verifying…"
      ) : (
        <div className={`alert alert-${state.ok ? "success" : "danger"}`}>
          {state.msg}
        </div>
      )}
      <Link to="/">Continue shopping</Link>
    </Shell>
  );
}
