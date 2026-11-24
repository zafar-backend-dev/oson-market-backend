package uz.osonmarket.reporting.admin;

import java.util.List;
import uz.osonmarket.reporting.admin.AdminStatisticsController.Section;

final class StatisticsQueries {
    private StatisticsQueries() {}

    static List<Query> forSection(Section section) {
        return switch (section) {
            case OVERVIEW -> List.of(one("totals", """
                    select (select count(*) from users) accounts,
                           (select count(*) from users where created_at >= :start and created_at < :end) new_accounts,
                           (select count(*) from products) products,
                           (select count(*) from categories) categories,
                           (select count(*) from brands) brands,
                           (select count(*) from branches) branches,
                           (select count(*) from orders where placed_at >= :start and placed_at < :end) orders,
                           (select count(*) from product_comments where deleted_at is null) visible_comments,
                           (select count(*) from product_likes) product_likes,
                           (select count(*) from order_notifications where read_at is null) unread_notifications
                    """), orderMoney(), timeline());
            case ORDERS -> List.of(orderMoney(), timeline(),
                    group("by_status", "status", "orders", "placed_at >= :start and placed_at < :end"),
                    group("by_fulfillment", "fulfillment_method", "orders", "placed_at >= :start and placed_at < :end"),
                    group("status_changes", "old_status, new_status", "order_status_history", "created_at >= :start and created_at < :end"),
                    group("ratings", "service_rating", "orders", "rated_at >= :start and rated_at < :end"),
                    many("top_variants", """
                        select i.variant_id, i.sku_snapshot, o.currency_code, count(distinct o.id) orders,
                               sum(i.quantity) units, sum(i.line_total) line_total, sum(i.discount_amount) discount
                        from order_items i join orders o on o.id = i.order_id
                        where o.placed_at >= :start and o.placed_at < :end and o.status in ('DELIVERED', 'PICKED_UP')
                        group by i.variant_id, i.sku_snapshot, o.currency_code
                        order by units desc, i.sku_snapshot, i.variant_id, o.currency_code limit :limit
                        """),
                    one("fulfillment", """
                        select count(distinct user_id) unique_customers,
                               count(*) filter (where status = 'CANCELLED' and cancellation_reason is not null) cancellations_with_reason,
                               count(*) filter (where service_rating is not null) rated_cohort_orders,
                               avg(service_rating) average_cohort_rating,
                               avg(delivery_distance_km) average_delivery_distance_km
                        from orders where placed_at >= :start and placed_at < :end
                        """));
            case CATALOG -> List.of(one("inventory", """
                    select (select count(*) from products) products,
                           (select count(*) from products where created_at >= :start and created_at < :end) new_products,
                           (select count(*) from products where brand_id is null) unbranded_products,
                           (select count(*) from categories) categories,
                           (select count(*) from categories where parent_id is null) root_categories,
                           (select count(*) from brands) brands,
                           (select count(*) from product_variants) variants,
                           (select count(*) from product_variants where is_default is true) default_variants,
                           (select count(*) from product_variant_images) variant_images,
                           (select count(*) from product_variants v where not exists
                               (select 1 from product_variant_images i where i.variant_id = v.id)) variants_without_images,
                           (select count(*) from variant_prices) price_records,
                           (select count(*) from products p where not exists
                               (select 1 from product_categories c where c.product_id = p.id)) uncategorized_products,
                           (select count(*) from product_variants v where not exists
                               (select 1 from variant_prices p where p.variant_id = v.id
                                 and (p.valid_from is null or p.valid_from <= current_timestamp)
                                 and (p.valid_to is null or p.valid_to > current_timestamp))) variants_without_current_price
                    """),
                    group("product_statuses", "status", "products", "true"),
                    group("product_kinds", "product_kind", "products", "true"),
                    group("variant_statuses", "status", "product_variants", "true"),
                    group("category_statuses", "active", "categories", "true"),
                    group("brand_statuses", "active", "brands", "true"),
                    group("product_translation_coverage", "language_code", "product_translations", "true"),
                    group("category_translation_coverage", "language_code", "category_translations", "true"),
                    many("top_categories", """
                        select c.id, c.slug, uz.name name_uz, ru.name name_ru, c.active, count(pc.product_id) products
                        from categories c left join product_categories pc on pc.category_id = c.id
                        left join category_translations uz on uz.category_id = c.id and uz.language_code = 'uz'
                        left join category_translations ru on ru.category_id = c.id and ru.language_code = 'ru'
                        group by c.id, uz.name, ru.name order by products desc, c.id limit :limit
                        """),
                    many("top_brands", """
                        select b.id, b.name, b.active, count(p.id) products
                        from brands b left join products p on p.brand_id = b.id
                        group by b.id order by products desc, b.id limit :limit
                        """));
            case CUSTOMERS -> List.of(one("accounts", """
                    select count(*) total,
                           count(*) filter (where u.created_at >= :start and u.created_at < :end) registrations,
                           count(*) filter (where u.email_verified_at is not null) verified,
                           count(*) filter (where u.google_subject is not null) google_linked,
                           count(*) filter (where nullif(btrim(p.first_name), '') is not null
                             and nullif(btrim(p.last_name), '') is not null
                             and nullif(btrim(p.phone), '') is not null) complete_profiles,
                           count(p.avatar_media_id) profiles_with_avatar
                    from users u left join user_profiles p on p.user_id = u.id
                    """),
                    group("by_role", "role", "users", "true"),
                    group("by_status", "status", "users", "true"),
                    group("by_language", "preferred_language_code", "users", "true"),
                    many("order_frequency", """
                        select orders_per_customer, count(*) customers
                        from (select user_id, count(*) orders_per_customer from orders
                              where user_id is not null and status <> 'CANCELLED'
                                and placed_at >= :start and placed_at < :end group by user_id) frequency
                        group by orders_per_customer order by orders_per_customer
                        """), timeline());
            case BRANCHES -> List.of(
                    group("by_status", "status, pickup_enabled, delivery_enabled", "branches", "true"),
                    many("branch_orders", """
                        select o.fulfillment_branch_id branch_id, b.name_uz, b.name_ru, o.currency_code,
                               o.fulfillment_method, count(*) orders,
                               count(*) filter (where o.status = 'CANCELLED') cancelled,
                               count(*) filter (where o.status in ('DELIVERED', 'PICKED_UP')) completed,
                               coalesce(sum(o.grand_total) filter (where o.status in ('DELIVERED', 'PICKED_UP')), 0) completed_total,
                               coalesce(sum(o.shipping_total) filter (where o.status = 'DELIVERED'), 0) delivered_shipping,
                               avg(o.delivery_distance_km) average_distance_km, avg(o.service_rating) average_rating
                        from orders o left join branches b on b.id = o.fulfillment_branch_id
                        where o.placed_at >= :start and o.placed_at < :end
                        group by o.fulfillment_branch_id, b.name_uz, b.name_ru, o.currency_code, o.fulfillment_method
                        order by orders desc, o.fulfillment_branch_id, o.currency_code, o.fulfillment_method limit :limit
                        """));
            case ENGAGEMENT -> List.of(one("comments", """
                    select count(*) created, count(*) filter (where parent_id is not null) replies,
                           count(*) filter (where deleted_at is not null) now_deleted,
                           count(*) filter (where edited_at is not null) ever_edited,
                           count(distinct author_id) authors
                    from product_comments where created_at >= :start and created_at < :end
                    """),
                    one("current_likes", """
                        select (select count(*) from product_likes) product_likes,
                               (select count(distinct user_id) from product_likes) product_likers,
                               (select count(*) from product_comment_likes l join product_comments c on c.id = l.comment_id
                                 where c.deleted_at is null) visible_comment_likes
                        """),
                    many("top_liked_products", """
                        select p.id, p.slug, count(*) likes from product_likes l join products p on p.id = l.product_id
                        group by p.id order by likes desc, p.id limit :limit
                        """),
                    many("top_commented_products", """
                        select p.id, p.slug, count(*) comments, count(*) filter (where c.parent_id is not null) replies
                        from product_comments c join products p on p.id = c.product_id
                        where c.deleted_at is null and c.created_at >= :start and c.created_at < :end
                        group by p.id order by comments desc, p.id limit :limit
                        """), timeline());
            case OPERATIONS -> List.of(
                    one("site", """
                        select exists(select 1 from site_logo) logo_configured,
                               exists(select 1 from site_about) about_configured,
                               (select count(*) from site_about_social_links) social_links,
                               (select count(*) from order_telegram_recipients) telegram_recipients,
                               (select count(*) from languages where enabled is true) enabled_languages,
                               (select count(*) from currencies) currencies
                        """),
                    many("media_by_type", """
                        select mime_type, count(*) files, coalesce(sum(size_bytes), 0) bytes
                        from media_files group by mime_type order by files desc, mime_type
                        """),
                    many("verification_codes", """
                        select purpose, count(*) issued, count(consumed_at) consumed,
                               count(*) filter (where consumed_at is null and expires_at <= current_timestamp) expired_unused
                        from auth_verification_codes where issued_at >= :start and issued_at < :end
                        group by purpose order by purpose
                        """),
                    one("notifications", """
                        select count(*) created, count(read_at) read, count(*) filter (where read_at is null) unread
                        from order_notifications where created_at >= :start and created_at < :end
                        """),
                    queue("email_queue", "order_status_emails"), queue("telegram_queue", "order_telegram_deliveries"));
        };
    }

    private static Query orderMoney() {
        return many("order_totals_by_currency", """
                select currency_code, count(*) orders,
                       count(*) filter (where paid_at is not null) marked_paid,
                       count(*) filter (where status in ('DELIVERED', 'PICKED_UP')) completed,
                       count(*) filter (where status = 'CANCELLED') cancelled,
                       coalesce(sum(subtotal), 0) placed_subtotal,
                       coalesce(sum(discount_total), 0) placed_discounts,
                       coalesce(sum(shipping_total), 0) placed_shipping,
                       coalesce(sum(grand_total), 0) placed_total,
                       coalesce(sum(grand_total) filter (where paid_at is not null and status <> 'CANCELLED'), 0) marked_paid_non_cancelled_total,
                       coalesce(sum(grand_total) filter (where status in ('DELIVERED', 'PICKED_UP')), 0) completed_total,
                       avg(grand_total) average_placed_total
                from orders where placed_at >= :start and placed_at < :end
                group by currency_code order by currency_code
                """);
    }

    private static Query timeline() {
        return many("daily", """
                with days as (
                    select cast(:fromDate as date) + generate_series(0, cast(:toDate as date) - cast(:fromDate as date)) as day
                ), events as (
                    select cast(placed_at at time zone :zone as date) as day, 'orders' metric
                    from orders where placed_at >= :start and placed_at < :end
                    union all
                    select cast(created_at at time zone :zone as date), 'registrations'
                    from users where created_at >= :start and created_at < :end
                    union all
                    select cast(created_at at time zone :zone as date), 'comments'
                    from product_comments where created_at >= :start and created_at < :end
                    union all
                    select cast(paid_at at time zone :zone as date), 'payments_marked'
                    from orders where paid_at >= :start and paid_at < :end
                ), counts as (
                    select day, count(*) filter (where metric = 'orders') orders,
                           count(*) filter (where metric = 'registrations') registrations,
                           count(*) filter (where metric = 'comments') comments,
                           count(*) filter (where metric = 'payments_marked') payments_marked
                    from events group by day
                )
                select d.day, coalesce(c.orders, 0) orders, coalesce(c.registrations, 0) registrations,
                       coalesce(c.comments, 0) comments, coalesce(c.payments_marked, 0) payments_marked
                from days d left join counts c on c.day = d.day order by d.day
                """);
    }

    private static Query group(String name, String fields, String table, String predicate) {
        return many(name, "select " + fields + ", count(*) total from " + table + " where " + predicate
                + " group by " + fields + " order by total desc, " + fields);
    }

    private static Query queue(String name, String table) {
        return one(name, """
                select count(*) total,
                       count(sent_at) sent,
                       count(*) filter (where sent_at >= :start and sent_at < :end) sent_in_range,
                       count(*) filter (where sent_at is null and next_attempt_at is not null) pending,
                       count(*) filter (where sent_at is null and next_attempt_at is null) stopped,
                       coalesce(sum(attempts), 0) retry_attempts
                from
                """ + table);
    }

    private static Query one(String name, String sql) { return new Query(name, sql, true); }
    private static Query many(String name, String sql) { return new Query(name, sql, false); }

    record Query(String name, String sql, boolean single) {}
}
