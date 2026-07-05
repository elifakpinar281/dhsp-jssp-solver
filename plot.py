import sys
import pandas as pd
import matplotlib.pyplot as plt


def parse_arguments():
    args = sys.argv[1:]
    if len(args) < 3 or len(args) % 2 == 0:
        print("Usage")
        sys.exit(1)

    algorithm_name = args[0]
    pair_args = args[1:]

    instances = []
    for i in range(0, len(pair_args), 2):
        instances.append((pair_args[i], pair_args[i + 1]))
    return algorithm_name, instances


def main():
    algorithm_name, instances = parse_arguments()

    fig, axes = plt.subplots(nrows=len(instances), ncols=2, figsize=(11, 4.2 * len(instances)))
    if len(instances) == 1:
        axes = axes.reshape(1, 2)

    for row, (csv_file, label) in enumerate(instances):
        data = pd.read_csv(csv_file)
        x = data["expansions"] / 1000.0
        total_ops = int(data["total_ops"].iloc[0])

        ax_mem = axes[row][0]
        ax_mem.plot(x, data["reached"] / 1e6, label="reached", linewidth=2)
        ax_mem.plot(x, data["frontier"] / 1e6, label="frontier", linewidth=2, linestyle="--")
        ax_mem.set_xlabel("expansions (thousands)")
        ax_mem.set_ylabel("stored states (millions)")
        ax_mem.set_title(f"{label} memory usage")
        ax_mem.legend()
        ax_mem.grid(True, alpha=0.3)

        ax_depth = axes[row][1]
        ax_depth.plot(x, data["max_depth"], color="tab:red", linewidth=2, label="max depth reached")
        ax_depth.axhline(
            total_ops, color="gray", linestyle=":", linewidth=1.5,
            label=f"goal depth ({total_ops})",
        )
        ax_depth.set_xlabel("expansions (thousands)")
        ax_depth.set_ylabel("search depth (scheduled operations)")
        ax_depth.set_title(f"{label} search progress")
        ax_depth.set_ylim(0, total_ops * 1.1)
        ax_depth.legend()
        ax_depth.grid(True, alpha=0.3)

    fig.suptitle(
        f"{algorithm_name} on JSSP: memory usage and search progress",
        fontsize=13,
    )
    fig.tight_layout(rect=[0, 0, 1, 0.97])

    safe_name = algorithm_name.lower().replace("*", "star").replace(" ", "_")
    output_base = f"{safe_name}_search_progress"

    fig.savefig(f"{output_base}.png", dpi=200, bbox_inches="tight")
    fig.savefig(f"{output_base}.pdf", bbox_inches="tight")
    print(f"saved: {output_base}.png and {output_base}.pdf")


if __name__ == "__main__":
    main()