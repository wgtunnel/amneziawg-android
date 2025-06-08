package junktag

import "fmt"

type TaggedJunkGeneratorHandler struct {
	generators []TaggedJunkGenerator
	length     int
}

func (handler *TaggedJunkGeneratorHandler) AppendGenerator(generators TaggedJunkGenerator) {
	handler.generators = append(handler.generators, generators)
	handler.length++
}

// validate that packets were defined consecutively
func (handler *TaggedJunkGeneratorHandler) Validate() error {
	seen := make([]bool, len(handler.generators))
	for _, generator := range handler.generators {
		if index, err := generator.nameIndex(); err != nil {
			return fmt.Errorf("name index: %w", err)
		} else {
			seen[index-1] = true
		}
	}

	for _, found := range seen {
		if !found {
			return fmt.Errorf("junk packet index should be consecutive")
		}
	}

	return nil
}

func (handler *TaggedJunkGeneratorHandler) Generate() [][]byte {
	var rv = make([][]byte, handler.length)
	for i, generator := range handler.generators {
		rv[i] = make([]byte, generator.packetSize)
		copy(rv[i], generator.generate())
	}

	return rv
}
