package io.hyperfoil.tools.h5m.docs;

import java.util.Comparator;
import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkus.qute.TemplateExtension;

/**
 * Qute helpers for the documentation side-nav (see templates/partials/side-nav.html).
 *
 * <p>The nav is derived entirely from the content tree: each top-level
 * folder under {@code content/} is a menu, described by its {@code index.md} section
 * page (its {@code title} is the menu label, its {@code weight} the menu order). The
 * other pages in the folder are the menu items, ordered by their own {@code weight}.
 * Nothing about the menu structure is declared elsewhere — adding a folder with an
 * {@code index.md} adds a menu; adding a page adds an item.
 *
 * <p>Drafts are already excluded from {@code site.pages} by Roq (unless
 * {@code site.draft=true}), so no draft filtering is needed here.
 *
 * <p>Roq's built-in {@code sortBy}/{@code filter} extensions only apply to collection
 * {@code DocumentPage}s, so these methods provide the equivalent for {@link NormalPage}s.
 */
@TemplateExtension
public class NavExtension {

    /** The section index page of each top-level content folder, ordered by {@code weight}. */
    public static List<NormalPage> sections(List<NormalPage> pages) {
        return pages.stream()
                .filter(NavExtension::isSection)
                .sorted(Comparator.comparingInt(NavExtension::weight))
                .toList();
    }

    /** The (non-index) pages inside {@code section}'s folder, ordered by {@code weight}. */
    public static List<NormalPage> items(List<NormalPage> pages, NormalPage section) {
        String folder = folderOf(section);
        return pages.stream()
                .filter(p -> !isSection(p) && folder.equals(folderOf(p)))
                .sorted(Comparator.comparingInt(NavExtension::weight))
                .toList();
    }

    /** A section index page is a top-level folder's {@code index.md} (i.e. exactly {@code <folder>/index.md}). */
    private static boolean isSection(NormalPage page) {
        String path = page.sourcePath();
        int slash = path.indexOf('/');
        return "index".equals(page.baseFileName()) && slash > 0 && path.indexOf('/', slash + 1) < 0;
    }

    /** First path segment of the page's source path, e.g. {@code overview/what-is-h5m.md} -> {@code overview}. */
    private static String folderOf(NormalPage page) {
        String path = page.sourcePath();
        int slash = path.indexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static int weight(NormalPage page) {
        Object weight = page.data("weight");
        return weight == null ? Integer.MAX_VALUE : ((Number) weight).intValue();
    }
}
