import { Link } from 'react-router-dom';

function Wrap({ href, children }) {
  if (!href) return <div className="banner-card">{children}</div>;
  if (href.startsWith('/')) return <Link to={href} className="banner-card">{children}</Link>;
  return <a href={href} className="banner-card" target="_blank" rel="noopener noreferrer">{children}</a>;
}

/** Banners managed in Admin > Website banners. Renders nothing when there are none. */
export default function BannerStrip({ banners }) {
  if (!banners || banners.length === 0) return null;
  return (
    <section className="banner-strip" aria-label="Announcements">
      <div className="container"><div className="banner-row">
        {banners.map((b) => (
          <Wrap key={b.id} href={b.linkUrl}>
            {b.imageUrl && <img src={b.imageUrl} alt="" loading="lazy" />}
            <span className="banner-text"><strong>{b.title}</strong>{b.subtitle && <span>{b.subtitle}</span>}</span>
          </Wrap>
        ))}
      </div></div>
    </section>
  );
}
