# CobblePulse — Cities & Claims

Gestion territoriale du serveur CobblePulse pour Minecraft 1.21.1 (Fabric) et Cobblemon 1.8.4 : claims à la balise (façon GriefPrevention), permissions très fines (façon Flan), villes et parcelles, économie de blocs passive, WorldBorders indépendantes par dimension, intégration Cobblemon, Clans CobblePulse, CobblePulse-Ranch, JourneyMap et cartes de Xaero.

Le même jar va sur le serveur et dans le modpack. Côté client, il apporte la touche contextuelle, les écrans natifs, le rendu 3D et les cartes. Un joueur sans le mod garde la protection, les commandes, les titres d'entrée et voit des blocs fantômes à la place du rendu 3D.

## Installation
| Côté | Mods |
|---|---|
| Serveur | Fabric Loader ≥ 0.16, Fabric API, Fabric Language Kotlin, **Cobblemon 1.8.4**, ce mod |
| Serveur (recommandé) | **LuckPerms**, Clans CobblePulse (tags et territoires de clan), CobblePulse-Ranch (Bloc de Pension protégé) |
| Modpack | ce mod ; optionnels : JourneyMap, Xaero's Minimap, Xaero's World Map (un, plusieurs ou aucun) |

Données : `world/cobblepulse_claims/` (un fichier JSON par claim et par joueur, écriture différée hors du fil principal). Configuration : `config/cobblepulse/claims/`.

## Outils (registre `cobblepulse`)
- **Balise de Territoire** (`cobblepulse:claim_wand`) : clic droit coin A (repère lumineux 3D), puis coin B (vérifie le solde et la WorldBorder, crée le territoire). Clic droit sur un coin d'un claim : redimensionnement. Deux coins dans son propre claim : **parcelle**. Maj + clic droit dans un claim : écran de gestion. Recette : éclat d'améthyste, lingot d'or, bâton.
- **Lunette Topographique** (`cobblepulse:claim_inspector`) : en main (principale ou secondaire), bordures des claims proches en 3D — vert : vous, cyan : vous êtes membre, bleu : votre clan, orange : autres joueurs, violet : administration — et WorldBorder translucide. Clic droit (bloc ou air) : résumé dans le chat (ville, propriétaire, tag de clan, superficie, typologie). Recette : longue-vue, boussole, lingot d'or.
- Les deux outils sont donnés à la première connexion (`give_tools_on_first_join`) et par `/claim outils`.

## Économie de blocs
150 blocs toutes les 30 minutes de jeu effectif (hors AFK, 5 min), 500 au départ, cumul passif plafonné à 5 000 (ajustable par grade). Le minuteur est sauvegardé en continu. Les crédits du staff dépassent le plafond sans le réinitialiser. Tout se règle dans l'écran d'administration ou `config.json`.

## Limite de territoires par joueur
`config/cobblepulse/claims/config.json` → `claims.max_claims_per_player` (10 par défaut ; 0 = illimité). Réglage du fichier uniquement, pris en compte par `/claimadmin reload`. Seuls les territoires principaux comptent (ni les parcelles ni les claims admin). `claims.luckperms_max_claims_override` (true) laisse les nœuds `cobblepulse.claims.limit.max_claims.<n>` du cahier des charges remplacer cette limite pour un grade ; à `false`, la config est la seule source.

## Touche contextuelle (K, réassignable)
- Dans son claim (ou un claim géré) : **ClaimManagerScreen** — nom de la ville, matrice des permissions (recherche, catégories A à F, sélecteur Autorisé / Interdit / Défaut, cadenas sur les permissions verrouillées), sous-panneau des commandes (tout autoriser, tout bloquer, liste noire, liste blanche), membres et rôles, parcelles, aperçu 2D et bouton du rendu 3D, bouton staff « Convertir en Claim Admin ».
- Dans le claim d'un tiers : fiche en lecture seule. Dans la nature : tableau de bord (claims, solde, compte à rebours du prochain palier).
- Maj + K (ou onglet Staff) : **AdminClaimsHubScreen** et **AdminPermissionsConfigScreen** (défauts globaux des permissions, Modifiable / Verrouillé, économie, crédits de blocs, WorldBorders par dimension).
- Options client (`/claimsoptions` ou bouton du tableau de bord) : affichage sur les cartes, WorldBorder sur les cartes, noms des villes, rendu 3D permanent.

## Commandes
| Commande | Rôle |
|---|---|
| `/claim` (`/claims`) | aide ; `info`, `liste`, `blocs`, `nom <nom>`, `abandonner`, `outils`, `menu`, `herite <true/false>` |
| `/trust <joueur\|public> [access\|container\|build\|manager]` | ajoute au claim actuel (build par défaut) ; `/trustall` sur tous ses claims |
| `/untrust <joueur\|public>`, `/untrustall <joueur>` | retire l'accès |
| `/trustlist` | membres du claim (ouvre aussi l'onglet Membres) |
| `/claimadmin` | interface staff ; `bypass [on\|off]`, `creation admin\|joueur`, `convertir admin\|joueur [pseudo]`, `supprimer [id]`, `info <id>`, `tp <id>`, `blocs <joueur> ajouter\|retirer\|definir <n>`, `bordure <dimension> activer\|desactiver\|centre <x> <z>\|cercle <r>\|rectangle <x> <z>`, `flag <flag> <visiteur\|membre> <allow\|deny\|default>`, `defaut …`, `verrou <flag> oui\|non`, `reglage weather\|time\|level_cap <valeur>`, `reload` |

Niveaux de confiance : **access** (boutons, leviers, lits, portes, soigneur et PC), **container** (+ coffres, tonneaux, shulkers, fours, Bloc de Pension), **build** (+ construction, récoltes, captures et combats), **manager** / Co-Maire (+ membres, parcelles, permissions).

## Permissions LuckPerms
| Nœud | Par défaut |
|---|---|
| `cobblepulse.claims.use`, `.create`, `.city_name`, `.subclaim`, `.trust`, `.accrue` | accordé |
| `cobblepulse.claims.limit.max_claims.<n>` (ou `.unlimited`, si `luckperms_max_claims_override`), `.max_blocks.<n>`, `.blocks_per_interval.<n>` | valeurs de `config.json` |
| `cobblepulse.staff.bypass`, `.inspect_all`, `.convert_admin`, `.delete_others` | opérateur (niveau 2) |
| `cobblepulse.admin.config`, `.worldborder`, `.give_blocks` | opérateur (niveau 2) |

Un refus explicite dans LuckPerms l'emporte toujours. Le staff garde la possibilité de jouer normalement avec `/claimadmin bypass off`.

## Permissions de claim (82)
Le référentiel complet du cahier des charges (catégories A à F) plus **`cobblemon.ranch_daycare`** : ouvrir un Bloc de Pension de CobblePulse-Ranch est interdit aux visiteurs, permis au propriétaire et aux joueurs ajoutés par `/trust` (niveau container ou plus). Valeurs par défaut et statut Modifiable / Verrouillé : `config/cobblepulse/claims/flags.json` ou l'écran d'administration. Les permissions d'environnement et d'administration (explosions, feu, liquides, pistons, grief des mobs, apparitions, soin à l'entrée, plafond de niveau, arène, inventaire conservé, invulnérabilité, faim, météo et heure figées, vol) s'appliquent à la zone entière.

## WorldBorders par dimension
`config/cobblepulse/claims/worldborders.json` : centre, cercle (rayon) ou rectangle (largeur X, longueur Z) pour chaque dimension, mods compris. Par défaut : Overworld cercle de 10 000, Nether rectangle 4 000 × 4 000, End cercle de 5 000 centré en (100, −50). Pas de claim au-delà, repoussement doux des joueurs, montures et Pokémon, téléportations hors limite annulées, grief bloqué aux abords (`edge_buffer`, 2 blocs).

## Cartes
JourneyMap (API v2, `PolygonOverlay`), Xaero's Minimap et Xaero's World Map (surlignage au bloc près) : territoires colorés par relation, infobulles au survol (nom, propriétaire, tag de clan), tracé de la WorldBorder. Les connecteurs ne se chargent que si la carte est installée.

## Tests automatiques (GitHub Actions)
À chaque push : compilation, auto-tests sur un vrai serveur (Cobblemon 1.8.4, LuckPerms, Clans, Ranch) sur deux démarrages, puis un vrai client avec JourneyMap et les cartes de Xaero. Résultats, captures et jar sur la branche `ci-output`.
